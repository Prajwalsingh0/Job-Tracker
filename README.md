# JobHunt — Full-Stack Job Application Tracker

A personal job-application tracker (CRM) rebuilt as a real full-stack application.

- **`jobhunt/`** — React 18 + TypeScript + Vite frontend (Tailwind CSS + shadcn/ui)
- **`backend/`** — Spring Boot 3.5 REST API (Java 17+, Maven, PostgreSQL)

The original app kept every user and every job in `localStorage` with a fake, client-side
login. The frontend is preserved as-is; all persistence and authentication now live in the
Spring Boot API.

---

## What changed

| Before | After |
| --- | --- |
| Users stored in `localStorage`, passwords in plain text | Users in PostgreSQL, passwords hashed with BCrypt |
| "Login" compared strings in the browser | JWT bearer tokens issued by the API, verified per request |
| Jobs/resumes stored in `localStorage` | Relational tables (`users`, `jobs`, `resumes`), scoped per user |
| Resume files as base64 strings in the browser | Uploaded via multipart, stored in the database, served by a download endpoint |
| Dashboard stats computed in the browser | Computed by `GET /api/jobs/stats` |
| Client-side search/filter | `GET /api/jobs?search=&status=` |

---

## Prerequisites

| Tool | Version used |
| --- | --- |
| Java | 17 or newer (developed and tested on JDK 23) |
| Maven | 3.9+ |
| Node.js | 18+ (tested on Node 22) |
| PostgreSQL | 14+ |

---

## 1. Generate a JWT secret (required)

The API signs its JWT access tokens with a secret that is **read only from the
`JWT_SECRET` environment variable**. There is no default and no fallback in the repository.
If `JWT_SECRET` is missing, or shorter than 32 characters, the application **refuses to
start** and explains why.

Generate a strong value once and keep it out of version control:

```bash
# macOS / Linux / Git Bash
openssl rand -base64 48

# Windows PowerShell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))

# Node.js
node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
```

Use the same secret every time you start the API — changing it invalidates all issued
tokens and forces users to sign in again. Never commit it; `.env` and `*.env` files are
git-ignored.

---

## 2. Set up the database

Create the database and a role (adjust names/passwords as you like):

```sql
CREATE DATABASE jobhunt;
CREATE USER jobhunt WITH PASSWORD 'change-me';
GRANT ALL PRIVILEGES ON DATABASE jobhunt TO jobhunt;
```

The schema is owned by **Flyway** migrations in `backend/src/main/resources/db/migration/`
(`postgresql/` and `h2/`). They run automatically on startup, and Hibernate is configured with
`ddl-auto: validate` — so the application refuses to start if the entities and the migrated schema
disagree. No manual DDL is required.

Prefer containers? The repository ships a `docker-compose.yml`:

```bash
POSTGRES_PASSWORD='choose-a-password' docker compose up -d
```

---

## 3. Run the backend

```bash
cd backend

# Set the secret in your shell (use the value you generated in step 1)
export JWT_SECRET='paste-your-generated-secret-here'      # Windows: $env:JWT_SECRET = '...'

# Optional: point at your database if the defaults don't match
export DB_USERNAME='jobhunt'
export DB_PASSWORD='change-me'

mvn spring-boot:run
```

The API starts on **http://localhost:8080**.

Configuration is read from environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | **none — required** | HMAC signing key for JWTs. At least 32 characters. The app fails fast if it is missing or too short. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/jobhunt` | JDBC URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Browser origins allowed to call the API |
| `SERVER_PORT` | `8080` | HTTP port |

### Run the packaged jar

```bash
cd backend
mvn package
JWT_SECRET='paste-your-generated-secret-here' java -jar target/jobhunt-backend-1.0.0.jar
```

### If startup fails

```
JWT_SECRET is not set. Set the JWT_SECRET environment variable to a random string of at
least 32 characters before starting the application. Generate one with: openssl rand -base64 48
```

or

```
JWT_SECRET is too short (N characters). It must be at least 32 characters (256 bits) for
HS256. Generate one with: openssl rand -base64 48
```

Both mean the environment variable is missing or too weak — the server deliberately exits
rather than start with an insecure signing key. Neither message ever contains the value.

---

## 4. Run the frontend

```bash
cd jobhunt
npm install
cp .env.example .env      # Windows: copy .env.example .env
npm run dev
```

Open **http://localhost:5173**.

The frontend reads the API location from `VITE_API_BASE_URL` (default `http://localhost:8080`),
so you can point it at a different backend without touching the code.

---

## 5. Tests and builds

```bash
# Backend: 14 integration tests (auth + job CRUD + isolation + resumes)
cd backend && mvn test

# Backend: run tests and build the jar
cd backend && mvn package

# Frontend: unit tests
cd jobhunt && npm run test:run

# Frontend: lint, then type-check and production build
cd jobhunt && npm run lint && npm run build
```

The same checks run in CI on every push and pull request (`.github/workflows/ci.yml`): backend
`mvn verify`, frontend lint + unit tests + build, and a scan that fails the build if an obvious
credential is committed.

Backend tests run against an **in-memory H2 database** in PostgreSQL compatibility mode
(`src/test/resources/application-test.yml`), so `mvn test` needs no database server and no
`JWT_SECRET` — the test profile supplies a throwaway randomly generated key that is never
used outside tests. The running application always uses PostgreSQL and always requires a
real `JWT_SECRET`.

---

## API overview

Base URL: `http://localhost:8080/api`. All endpoints except register/login require
`Authorization: Bearer <token>`.

Interactive documentation (OpenAPI 3) is served by the backend itself:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Raw spec: <http://localhost:8080/v3/api-docs>

Use the **Authorize** button in Swagger UI to paste a JWT and call protected endpoints directly.

### Authentication

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Create an account, returns a JWT |
| `POST` | `/api/auth/login` | Exchange credentials for a JWT |
| `GET` | `/api/auth/me` | Current user profile |

### Jobs

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/jobs?search=&status=&page=&size=&sort=&direction=` | Paged job list — filtering, sorting and paging all run in the database |
| `GET` | `/api/jobs/export?search=&status=` | CSV export of the same filtered set |
| `GET` | `/api/jobs/{id}` | Fetch one job |
| `POST` | `/api/jobs` | Create a job |
| `PUT` | `/api/jobs/{id}` | Update a job |
| `PATCH` | `/api/jobs/{id}/status` | Move a job to another pipeline stage |
| `DELETE` | `/api/jobs/{id}` | Delete a job |
| `GET` | `/api/jobs/stats` | Dashboard metrics |

Job statuses: `wishlist`, `applied`, `phone_screen`, `interview`, `offer`, `rejected`,
`withdrawn`, `ghosted`.

Sorting accepts `updatedAt` (default), `createdAt`, `companyName`, `jobTitle`, `status`,
`appliedDate` and `targetApplyDate`, with `direction=asc|desc`. Page size defaults to 20 and is
capped at 500. Creating a job whose company **and** title already exist for the same user returns
`409 Conflict`, which stops the same role being tracked twice by accident.

### Resumes

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/resumes` | List resume metadata (with usage counts) |
| `POST` | `/api/resumes` | Upload a PDF/DOCX (`multipart/form-data`) |
| `GET` | `/api/resumes/{id}/download` | Download the document |
| `DELETE` | `/api/resumes/{id}` | Delete a resume (jobs keep existing, detached) |

Errors always use the same JSON shape:

```json
{
  "timestamp": "2026-01-01T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/jobs",
  "fieldErrors": { "companyName": "Company name is required" }
}
```

---

## Smoke-testing the API by hand

```bash
# Register (JWT_SECRET must be exported in the server's shell first)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Ada","email":"ada@example.com","password":"change-me"}'

# Create a job (paste the token from the response above)
curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -d '{"companyName":"Acme","jobTitle":"Backend Engineer","status":"applied"}'

# Dashboard stats
curl http://localhost:8080/api/jobs/stats -H "Authorization: Bearer <TOKEN>"
```

---

## Secret handling

- `JWT_SECRET` is the only credential the application reads, and it has **no committed
  fallback** — a missing or weak value stops startup.
- It is never written to source code, logs, error messages, or this documentation.
- `.env` files and build output are excluded via `.gitignore`; only `jobhunt/.env.example`
  (a template with no values) is tracked.
- Passwords are stored only as BCrypt hashes, and the hash is never returned by the API.

## Known limitations

- **PostgreSQL was not executed in this environment.** No PostgreSQL server or Docker was
  available, so the schema and queries were verified against H2 in PostgreSQL-compatibility
  mode. Please run the backend once against a real PostgreSQL instance before relying on it.
- Interview tracking exists in the frontend types but has no backend table or endpoint yet.
- Access tokens are stored in `localStorage`; there is no refresh-token flow or logout
  revocation (the token simply expires).
- The `h2/` migration is a hand-maintained twin of the `postgresql/` one, so every future schema
  change has to be written twice.
- Resume list endpoints load the document bytes with the entity; for large libraries move
  the files to object storage and keep only references in the database.
- The `uuid` npm packages are no longer used by the frontend (ids come from the database).
