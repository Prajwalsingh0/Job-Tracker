# Testing

## Running the suites

```bash
# Backend - 79 tests, no database server and no JWT_SECRET required
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

## Backend coverage — 79 tests

| Class | Tests | What it covers |
| --- | --- | --- |
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

## Database in tests

Tests run against **H2 in PostgreSQL-compatibility mode** with Flyway applying the same migration
pipeline as production, then Hibernate validating the entities against the migrated schema. A drift
between the migrations and the entities therefore fails the build.

The test profile pins `app.ai.api-key` to empty so a developer's real key can never change test
behaviour.

## What is **not** verified

Stated plainly so the green numbers are not over-read:

1. **PostgreSQL has never been executed against a real server.** No PostgreSQL instance or Docker
   daemon was available in the development environment. The schema, queries and migrations are
   proven against H2 in compatibility mode. `docker-compose.yml` makes this a two-command check —
   do it before relying on it.
2. **The live AI provider path is untested.** No API key was available (and inventing one would be
   wrong). Prompt construction, task routing and error handling are covered with a stubbed provider;
   the outbound HTTP call is not.
3. **No browser or end-to-end tests.** There is no Playwright/Cypress suite driving a real browser,
   so rendering, drag-and-drop behaviour and responsive layouts are verified by build and inspection
   rather than by automation.
4. **No load or performance testing.**
5. **Upload parsing is not tested** against real-world PDF/DOCX files — only the signature check is.
6. **Dark mode is not implemented**, so it is neither tested nor available.
