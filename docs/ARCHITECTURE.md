# Architecture

How the pieces fit together, and why they are arranged this way.

## Components

```
                    ┌──────────────────────────────────────────────┐
                    │  Browser                                     │
                    │  React 18 + TypeScript + Vite (SPA)          │
                    │                                              │
                    │  AuthContext ── access token held in memory  │
                    │  JobContext  ── jobs / resumes / stats       │
                    │  lib/api.ts  ── single HTTP client           │
                    └───────────────┬──────────────────────────────┘
                                    │
              HTTPS · Bearer access token · httpOnly refresh cookie
                                    │
                    ┌───────────────▼──────────────────────────────┐
                    │  nginx (frontend image)                      │
                    │  serves the SPA, proxies /api → backend      │
                    │  adds CSP and response hardening             │
                    └───────────────┬──────────────────────────────┘
                                    │
                    ┌───────────────▼──────────────────────────────┐
                    │  Spring Boot 3.5 API                         │
                    │                                              │
                    │  controller  → validate, delegate            │
                    │  service     → business rules, transactions  │
                    │  repository  → Spring Data JPA               │
                    │                                              │
                    │  security/   JwtService · JwtAuthFilter ·    │
                    │              SecurityConfig · RateLimit      │
                    │  ai/         AiProvider · SkillMatcher       │
                    │  storage/    FileStorageService (local FS)   │
                    │  exception/  GlobalExceptionHandler          │
                    └───────┬────────────────────────┬─────────────┘
                            │                        │
                   JPA / Hibernate            file system
                            │                        │
                    ┌───────▼──────────┐   ┌─────────▼────────────┐
                    │  PostgreSQL      │   │  STORAGE_ROOT        │
                    │  schema owned    │   │  uploads (UUID keys) │
                    │  by Flyway       │   │                      │
                    └──────────────────┘   └──────────────────────┘
```

## Data model

| Table | Purpose |
| --- | --- |
| `users` | account, BCrypt password hash |
| `jobs` | one tracked application: company, title, status, dates, salary, source, work mode |
| `job_tags` | free-form labels per job (collection table) |
| `job_status_history` | one row per pipeline transition, used for the timeline and analytics |
| `resumes` | resume metadata plus a `storage_key`; the document itself is on disk |
| `cover_letters` | pasted text and/or an uploaded document, optionally linked to a job |
| `refresh_tokens` | SHA-256 hash of each issued refresh token, revoked on rotation |
| `flyway_schema_history` | migration bookkeeping |

Every row that belongs to a person carries `user_id`, and **every query is scoped by it** — no
endpoint can read or modify another account's data. Cross-user access returns `404`, not `403`,
so the API does not confirm that someone else's record exists.

## Request flow

1. `JwtAuthenticationFilter` reads the bearer token, validates the signature and expiry, loads the
   user, and populates the security context.
2. `AuthRateLimitFilter` guards the credential endpoints per client IP.
3. The controller validates the payload with Bean Validation and delegates.
4. The service applies business rules inside a transaction and returns a DTO — **entities are never
   serialized**, so a password hash or storage key cannot leak by accident.
5. Failures become one uniform JSON error shape via `GlobalExceptionHandler`.

## Key decisions and their reasons

| Decision | Why |
| --- | --- |
| Flyway owns the schema; Hibernate runs `validate` | Schema changes are reviewable and reversible, and entity/schema drift fails the build instead of silently altering a table |
| Two migration folders (`postgresql/`, `h2/`) | Tests need a database without a server; H2 rejects `BYTEA`, so the blob column is the only divergence. The PostgreSQL scripts are additionally verified by a real PostgreSQL server started in-process during the test run |
| Access token in memory, refresh token in an httpOnly cookie | A stored access token is readable by any injected script; a cookie is not |
| Refresh tokens stored as SHA-256 hashes, rotated on use | A database leak yields nothing usable, and replay revokes the family |
| Documents on disk behind `FileStorageService` | Database blobs made the list endpoint read every file; the interface also makes object storage a drop-in |
| Deterministic skill matching, LLM only for generation | A number presented as an "ATS score" would be a false claim; keyword overlap is honest and testable |
| `AiProvider` with a no-op default | The app runs without a key, and generation says "not configured" instead of inventing text |
| Charts as CSS bars, no charting library | Avoids ~500 KB for a funnel and a grouped bar chart |
| Tags as a collection table | Simpler than a tag entity, and sufficient for label-style filtering |

## Deliberate trade-offs

- Analytics loads the user's jobs and reduces them in memory. Correct and simple at personal scale;
  SQL aggregates or a warehouse are the next step if volume grows.
- Refresh-token rate limiting is in-process. A multi-instance deployment should move the counter to
  Redis or a gateway.
- Cover letters are capped at 20,000 characters and returned in full in list responses. Fine for a
  personal library; a detail endpoint would be needed at scale.
- Matching uses text the caller pastes, not the stored PDF/DOCX. Text extraction would need a parsing
  dependency.
