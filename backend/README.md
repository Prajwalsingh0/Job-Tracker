# JobHunt Backend

Spring Boot 3.5 REST API for the JobHunt job-application tracker. Replaces the original
browser-only `localStorage` persistence and fake client-side login.

## Stack

- Java 17+ (developed on JDK 23)
- Spring Boot 3.5.5 — Web, Data JPA, Security, Validation
- PostgreSQL (runtime), H2 (tests only)
- JJWT 0.11.5 for JSON Web Tokens
- Maven

## Package structure

```
com.jobhunt
├── JobHuntApplication        # entry point
├── controller/               # REST endpoints (thin, delegate to services)
├── service/                  # business logic + transactions
├── repository/               # Spring Data JPA interfaces
├── entity/                   # JPA entities and enums
├── dto/                      # request/response records (entities are never exposed)
├── security/                 # JWT service, filter, Spring Security config, CORS
└── exception/                # typed exceptions + global exception handler
```

## Running

```bash
# JWT_SECRET is required - see "JWT secret" below
export JWT_SECRET='paste-your-generated-secret-here'   # Windows: $env:JWT_SECRET = '...'

mvn spring-boot:run           # http://localhost:8080
mvn test                      # 14 integration tests; Flyway migrates H2, no database or JWT_SECRET needed
mvn package                   # tests + executable jar
```

### Configuration

All settings except `JWT_SECRET` have local defaults and can be overridden with environment
variables:

| Variable | Default |
| --- | --- |
| `JWT_SECRET` | **none — required.** At least 32 characters; startup fails without it. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/jobhunt` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `JWT_EXPIRATION_MS` | `86400000` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` |
| `SERVER_PORT` | `8080` |

## JWT secret

`app.jwt.secret` resolves to `${JWT_SECRET:}` — an environment variable with **no committed
fallback value**. `JwtService` validates it during bean creation, so a bad value aborts
startup before the HTTP port is opened:

| Condition | Result |
| --- | --- |
| `JWT_SECRET` unset or blank | Startup fails: *"JWT_SECRET is not set…"* |
| `JWT_SECRET` shorter than 32 characters | Startup fails: *"JWT_SECRET is too short (N characters)…"* |
| `JWT_SECRET` ≥ 32 characters | Startup proceeds; HS256 signing key derived via `Keys.hmacShaKeyFor` |

Why 32 characters: HS256 needs a 256-bit key, and a UTF-8 encoded string is never shorter
than its character count, so ≥ 32 characters always yields ≥ 32 bytes.

The secret value is never logged, never echoed in an error message, and never returned by
any endpoint. Generate one with:

```bash
openssl rand -base64 48
```

The **test profile does not need `JWT_SECRET`**. `src/test/resources/application-test.yml`
sets `app.jwt.secret` from `${random.uuid}${random.uuid}`, so every test run gets a fresh
throwaway key with no secret literal stored anywhere in the repository. That key exists only
for the lifetime of the test JVM and is unrelated to any real deployment.

## Database

Create the database once. The schema itself is created and evolved by **Flyway**; Hibernate runs with
`ddl-auto: validate` and refuses to start if the entities drift from the migrated schema.

```sql
CREATE DATABASE jobhunt;
CREATE USER jobhunt WITH PASSWORD 'change-me';
GRANT ALL PRIVILEGES ON DATABASE jobhunt TO jobhunt;
```

A PostgreSQL container is provided for local work:

```bash
POSTGRES_PASSWORD='choose-a-password' docker compose up -d
```

### Migrations

Migrations live in `src/main/resources/db/migration/` with Flyway locations set to
`classpath:db/migration/{vendor}`:

| Folder | Used by |
| --- | --- |
| `postgresql/` | the running application |
| `h2/` | the test suite (H2 in PostgreSQL-compatibility mode) |

Both folders must contain the same numbered migration. The H2 copy differs only where PostgreSQL
types are unavailable (`BYTEA` becomes `BINARY VARYING`). To make a change, add `V2__<description>.sql`
to **both** folders — the build fails if the migrated schema and the entities disagree.

An existing database that predates Flyway already has the tables but no `flyway_schema_history`.
Baseline it once instead of letting Flyway try to re-create the tables:

```bash
mvn flyway:baseline \
  -Dflyway.url=jdbc:postgresql://localhost:5432/jobhunt \
  -Dflyway.user=postgres -Dflyway.password='***' \
  -Dflyway.baselineVersion=1 -Dflyway.baselineDescription=baseline
```

### Tables

- `users` — id, name, email (unique), password_hash (BCrypt), created_at
- `jobs` — id, user_id → users, company_name, job_title, job_url, description, location,
  salary_range, status, applied_date, target_apply_date, outcome, outcome_reason, feedback,
  notes, resume_id → resumes, created_at, updated_at
- `resumes` — id, user_id → users, name, file_name, content_type, file_type, version_tag,
  file_size, file_data, created_at

Every job and resume row belongs to exactly one user, and all queries are scoped by that
owner — one account can never read or modify another account's data.

## Security model

- Passwords are hashed with **BCrypt**; the hash is never returned by the API.
- Access tokens are short-lived HS256 JWTs; refresh tokens are opaque 256-bit values stored only as
  SHA-256 hashes, rotated on every use, with replay revoking the whole family.
- The refresh token travels in an `httpOnly` cookie scoped to `/api/auth`; the access token is
  returned in the body and kept in memory by the frontend.
- `POST /api/auth/register`, `/login`, `/refresh` and `/logout` are the only public endpoints.
- Every other request needs `Authorization: Bearer <JWT>`.
- The signing key comes only from `JWT_SECRET`, and startup fails if it is missing or weak.
- Sessions are stateless on the server apart from the refresh-token table.
- Unauthenticated requests get a JSON `401`; forbidden ones get a JSON `403`.
- CORS is an explicit origin allow-list with credentials enabled (never `*`).
- Uploads are verified by file signature, not by the browser-supplied content type.

## Endpoints

Interactive OpenAPI 3 documentation is served by the application:

| URL | What it is |
| --- | --- |
| `/swagger-ui.html` | Swagger UI — paste a JWT with **Authorize** and call protected endpoints |
| `/v3/api-docs` | Raw OpenAPI JSON |

Both are permitted in `SecurityConfig` (documentation only, no data access).

### Auth — `/api/auth`

| Method | Path | Auth | Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/register` | — | `{name, email, password}` | `201 {token, tokenType, user}` + refresh cookie |
| POST | `/login` | — | `{email, password}` | `200 {token, tokenType, user}` + refresh cookie |
| POST | `/refresh` | cookie | — | `200 {token, tokenType, user}` + rotated cookie |
| POST | `/logout` | cookie | — | `204`, revoked tokens, cookie cleared |
| GET | `/me` | Bearer | — | `200 {id, name, email}` |

`/refresh` and `/logout` are driven by the `jobhunt_refresh` httpOnly cookie rather than by an
access token, so they stay reachable after the 15-minute access token expires. `/login`,
`/register` and `/refresh` are rate limited per client IP and answer `429` with `Retry-After` once
the window is exhausted.

### Jobs — `/api/jobs`

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| GET | `?search=&status=&page=&size=&sort=&direction=` | — | `200 PageResponse<JobDto>` |
| GET | `/export?search=&status=` | — | `200 text/csv` (UTF-8 BOM, RFC 4180 quoting) |
| GET | `/{id}` | — | `200 JobDto` |
| POST | `/` | JobRequest | `201 JobDto` |
| PUT | `/{id}` | JobRequest | `200 JobDto` |
| PATCH | `/{id}/status` | `{status}` | `200 JobDto` |
| DELETE | `/{id}` | — | `204` |
| GET | `/stats` | — | `200 JobStatsDto` |
| GET | `/activity` | — | `200 [JobStatusHistoryDto]` — newest transitions across your jobs |
| GET | `/{id}/history` | — | `200 [JobStatusHistoryDto]` — oldest first |

`JobRequest`:

```json
{
  "companyName": "Acme Corp",
  "jobTitle": "Backend Engineer",
  "jobUrl": "https://example.com/jobs/1",
  "description": "…",
  "location": "Berlin",
  "salaryRange": "$120k - $150k",
  "status": "applied",
  "appliedDate": "2026-01-15",
  "targetApplyDate": null,
  "outcomeReason": null,
  "feedback": null,
  "notes": "Referred by a friend",
  "jobSource": "LinkedIn",
  "workMode": "hybrid",
  "deadline": "2026-10-01",
  "salaryMin": 90000,
  "salaryMax": 120000,
  "salaryCurrency": "USD",
  "tags": ["referral", "dream job"],
  "resumeId": 3
}
```

`workMode` accepts `remote`, `hybrid` or `onsite`. `salaryCurrency` is upper-cased on the way in, and
`tags` are trimmed, de-duplicated, capped at 20 and returned sorted. Supplying a `salaryMin` greater
than `salaryMax` returns `400`.

Business rules applied by the service:

- `companyName` and `jobTitle` are required; optional text fields have length limits.
- Filtering, sorting and pagination are executed in the database via JPA specifications, not in
  memory. Sortable fields are whitelisted (`updatedAt`, `createdAt`, `companyName`, `jobTitle`,
  `status`, `appliedDate`, `targetApplyDate`); an unknown field falls back to `updatedAt`.
- Creating a job with a company **and** title that already exist for the user returns `409`.
  Updating the same job excludes itself from that check.
- A job in `wishlist` has no `appliedDate`; moving it to any other status stamps one
  (the supplied date, the previously stored one, or today).
- `outcome` is derived from `status` (`offer`/`rejected`/`withdrawn`/`ghosted`) so the two
  can never disagree.
- Every creation and every status change is written to `job_status_history`. Setting a status to
  the value it already has does **not** add a row, so the timeline stays meaningful.
- `resumeId` must reference one of *your* resumes, otherwise the request returns `404`.

### Resumes — `/api/resumes`

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| GET | `/` | — | `200 [ResumeDto]` (metadata + `usageCount`) |
| POST | `/` | `multipart/form-data`: `file`, optional `name`, `versionTag` | `201 ResumeDto` |
| GET | `/{id}/download` | — | `200` file bytes with `Content-Disposition` |
| DELETE | `/{id}` | — | `204` |

Uploads accept **PDF and DOCX only**, up to 10 MB, and the bytes are verified against the file
signature (`%PDF-` or `PK\x03\x04`) rather than trusting the browser content type. Documents are
written to file storage (see below) and downloads are streamed. Deleting a resume removes the
stored file and detaches it from any job that referenced it; the jobs themselves are kept.

### Cover letters — `/api/cover-letters`

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| GET | `/` | — | `200 [CoverLetterDto]` |
| POST | `/` | `multipart/form-data`: optional `file`, `body`, `name`, `versionTag`, `jobId` | `201 CoverLetterDto` |
| GET | `/{id}/download` | — | `200` document bytes (only when a file was uploaded) |
| DELETE | `/{id}` | — | `204` |

A letter is an uploaded PDF/DOCX, pasted text, or both — at least one is required, otherwise the
request returns `400`. Documents use the same storage, size limit and signature validation as
resumes. `jobId` must reference one of *your* jobs, and deleting that job clears the link rather
than removing the letter. Text is capped at 20,000 characters.

## File storage

Document bytes are **not** stored in the database. `FileStorageService` is the seam:

| Member | Purpose |
| --- | --- |
| `store(bytes, fileName)` | Writes the content and returns an opaque key |
| `loadAsResource(key)` | Returns a Spring `Resource`, so downloads stream instead of buffering |
| `delete(key)` | Removes the file; missing files are ignored |

The default implementation is `LocalFileStorageService`, which writes under `STORAGE_ROOT`
(default `./data/uploads`, git-ignored) using a generated UUID name — the user-supplied file name
never influences the path. Keys are resolved inside the root and anything that escapes it is
rejected, so a crafted key cannot read or write arbitrary files.

Because the column no longer holds content, listing resumes is a metadata-only query. Swapping in
object storage means adding another `FileStorageService` implementation; no service code changes.

### Legacy blob backfill

Older rows may still hold their document in the `file_data` column. A startup runner moves them into
file storage and clears the column. It is idempotent, only selects rows with bytes and no storage
key, and a failure is logged as a warning rather than blocking startup so the next run can retry.

## Error format

```json
{
  "timestamp": "2026-01-01T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/jobs",
  "fieldErrors": { "jobTitle": "Job title is required" }
}
```

| Status | Meaning |
| --- | --- |
| 400 | Validation failure, malformed body, unknown enum value, bad file type |
| 401 | Missing/invalid token, wrong credentials |
| 403 | Authenticated but not allowed |
| 404 | Not found **or** owned by another user |
| 409 | Duplicate email / data conflict |
| 413 | Upload larger than 10 MB |
| 500 | Unexpected server error |

## Tests

`src/test/java/com/jobhunt/`:

- **`AuthApiTest`** (6) — registration, login, profile, duplicate email, wrong password,
  unknown email, validation errors, protected endpoints.
- **`JobApiTest`** (8) — full job lifecycle, status transitions, search/filter, dashboard
  statistics, validation, cross-user isolation, resume upload/download/attach/detach,
  unsupported file types, and attaching another user's resume.

They run against in-memory H2 in PostgreSQL compatibility mode
(`src/test/resources/application-test.yml`), so no database server and no `JWT_SECRET` are
needed:

```bash
mvn test
```
