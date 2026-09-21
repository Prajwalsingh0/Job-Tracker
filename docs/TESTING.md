# Testing

## Running the suites

```bash
# Backend - 85 tests; no database server needed (a real PostgreSQL is started in-process)
cd backend && mvn test

# Backend - tests plus the packaged jar
cd backend && mvn package

# Frontend - 25 unit tests
cd jobhunt && npm run test:run

# Frontend - watch mode
cd jobhunt && npm run test

# Frontend - lint, then type-check and production build
cd jobhunt && npm run lint && npm run build
```

CI (`.github/workflows/ci.yml`) runs `mvn verify`, the frontend lint + tests + build, and a scan that
fails the build if an obvious credential is committed.

## Backend coverage — 85 tests

| Class | Tests | What it covers |
| --- | --- | --- |
| `PostgresIntegrationTest` | 4 | **Runs against a real PostgreSQL 14.22 server started inside the test JVM.** Flyway applies the actual `db/migration/postgresql` scripts and Hibernate validates the entities against what was created; then a full job lifecycle (tags, enums, dates, numeric columns), status history with timestamps, paging, analytics, `BYTEA` document round-trip and cover letters are exercised on the real engine |
| `ProdProfileTest` | 2 | The prod profile **refuses to start without credentials** (fail-closed, as documented) and **does start** with them against a real PostgreSQL, with Flyway and JPA present |
| `AuthApiTest` | 6 | Registration, login, profile, duplicate email, wrong password, unknown email, validation errors, protected endpoints |
| `AuthSecurityTest` | 7 | httpOnly refresh cookie, no token leakage in the body, rotation, replay rejection, missing cookie, logout revocation, security headers, upload signature checks |
| `AuthRateLimitTest` | 2 | Failed logins are limited after the threshold, correct password is refused while the window is open, and normal authenticated traffic is unaffected |
| `JobApiTest` | 22 | CRUD, status transitions, appliedDate stamping, paging metadata, sort direction, unknown-sort fallback, CSV quoting and filters, duplicate guard including self-edit, stats, validation, user isolation, resume attach/detach |
| `JobApiTest` (details) | — | Job details round-trip with tag normalisation, salary range validation, unknown work mode, status history across transitions, no row for a no-op status change, activity ordering, history isolation |
| `ResumeStorageTest` | 6 | Document on disk with a byte-free database row, no storage-key leakage, byte-exact streaming, file removal on delete, cross-user download rejection, path-traversal rejection |
| `CoverLetterApiTest` | 10 | Text-only, document-only, both, neither, oversized text, disguised files, streaming download, delete cleanup, isolation, job-link ownership |
| `AnalyticsApiTest` | 9 | Metrics and funnel order, timeline entries from history, company/role breakdowns, date-range filtering, inverted-range rejection, deadline horizon and sorting, active interviews, isolation, authentication |
| `SkillMatcherTest` | 6 | Word-boundary matching, lookalikes (Java vs JavaScript), subsumption, case-insensitivity, empty and null input, scoring |
| `AiApiTest` | 7 | Unconfigured provider status, deterministic matching, honest disclaimer, no-description refusal, validation, isolation, `503` instead of fabricated content, authentication |
| `AiGenerateTest` | 4 | Provider text returned with the provider name, prompt contains only real user data, system prompt forbids invention and ATS claims, distinct instructions per task, unknown task rejected, isolation |

## Frontend coverage — 25 tests

| File | Tests | What it covers |
| --- | --- | --- |
| `types/index.test.ts` | 3 | Every status has a label and colour; kanban column ids are unique and map to known statuses; the board covers every status |
| `lib/api.test.ts` | 15 | Token storage, login payload, bearer header, query parameters, 401 handling, field-error precedence, 204 handling, unreachable server, paging parameters, CSV export, refresh-on-401 with retry, shared refresh for concurrent 401s, no refresh after a failed login, giving up on a dead refresh token |
| `components/ui/StatusBadge.test.tsx` | 3 | Label rendering, status colour, extra classes |
| `components/ui/ui-primitives.test.tsx` | 4 | Confirm dialog semantics and callbacks, Escape to cancel, toast rendering, `aria-live` region |

## Databases in tests

Two engines, deliberately:

- **H2 in PostgreSQL-compatibility mode** backs the fast API tests. Flyway applies the `h2/`
  migration and Hibernate validates against it, so a drift between migrations and entities fails the
  build.
- **A real PostgreSQL 14.22** is started in-process by `PostgresIntegrationTest`
  (`io.zonky.test:embedded-postgres`). It applies the `postgresql/` migration scripts and runs the
  same validate pass, which is what proves the migration is correct for the database the application
  actually uses. The first run downloads roughly 80 MB of PostgreSQL binaries; afterwards they are
  cached in the Maven repository.

The test profile pins `app.ai.api-key` to empty so a developer's real key can never change test
behaviour.

## What is **not** verified

Stated plainly so the green numbers are not over-read:

1. **A hosted PostgreSQL.** The suite proves the schema and queries on a local PostgreSQL 14.22.
   A managed instance with different version, SSL settings or a connection pooler is not covered.
2. **The live AI provider path is untested.** No API key was available (and inventing one would be
   wrong). Prompt construction, task routing and error handling are covered with a stubbed provider;
   the outbound HTTP call is not.
3. **No browser or end-to-end tests.** Rendering, drag-and-drop and responsive layouts are verified by
   build and inspection rather than by automation.
4. **No Docker image build test.** The Dockerfiles are not built in CI, so they are verified by
   inspection only.
5. **No load or performance testing.**
6. **Upload parsing is not tested** against real-world PDF/DOCX files — only the signature check is.
7. **Dark mode is not implemented**, so it is neither tested nor available.
