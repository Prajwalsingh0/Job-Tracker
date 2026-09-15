# JobHunt — Phase 0 Audit & Prioritized Roadmap

Audit performed against commit `c96df00` (branch `main`, in sync with `origin/main`, working tree clean).
Nothing in this document has been implemented yet.

---

## 1. Verified baseline

| Check | Command | Result |
| --- | --- | --- |
| Backend tests | `cd backend && mvn test` | **14/14 pass** (BUILD SUCCESS) |
| Frontend build | `cd jobhunt && npm run build` | **success** (`tsc -b && vite build`) |
| Frontend lint | `cd jobhunt && npm run lint` | **0 errors, 2 warnings** (`react-refresh/only-export-components` in both context files) |
| Repo state | `git status` | clean, 0 unpushed commits |

**Code size:** 37 Java files (~2,150 LOC incl. tests), 28 frontend files (~1,800 LOC).

---

## 2. Architecture as-is

```
┌─────────────────────────────────────────────┐
│ React 18 + TS + Vite  (jobhunt/)            │
│  Login · Register · Dashboard · Pipeline     │
│  AllJobs · ResumeLibrary                     │
│  AuthContext (JWT in localStorage)           │
│  JobContext  (jobs/resumes/stats)            │
│  lib/api.ts  (fetch wrapper, typed)          │
└──────────────────┬──────────────────────────┘
                   │ HTTP + Bearer JWT  (CORS: 5173 only)
┌──────────────────▼──────────────────────────┐
│ Spring Boot 3.5  (backend/)                 │
│  controller → service → repository          │
│  security: JwtService · JwtAuthFilter ·     │
│            SecurityConfig (stateless)       │
│  exception: GlobalExceptionHandler          │
│  DTOs (records) — entities never exposed    │
└──────────────────┬──────────────────────────┘
                   │ JPA / Hibernate 6.6
┌──────────────────▼──────────────────────────┐
│ PostgreSQL  (runtime)  ·  H2 (tests only)   │
│  users · jobs · resumes                     │
│  schema created by ddl-auto: update         │
└─────────────────────────────────────────────┘
```

Clean layering, DTO boundary, and per-user ownership scoping are already in place. The gaps below are
almost entirely about **breadth** (features listed in your phases that were never built) and
**production hardening** (migrations, CI, file storage, observability), not about the core design.

---

## 3. Audit findings

### 3.1 Working and verified

- Email/password auth with **BCrypt** hashing; `JWT_SECRET` mandatory and fail-fast (verified by executing
  the app with a missing and a short secret).
- **JWT bearer auth**, stateless; all endpoints except register/login protected; 401/403 returned as JSON.
- Job **CRUD, status transitions, derived outcome, appliedDate stamping**, all scoped per user.
- **Ownership isolation** enforced on every job/resume query and covered by tests.
- **Dashboard statistics** computed server-side.
- **Search + status filter** via `GET /api/jobs?search=&status=`.
- **Resume upload (PDF/DOCX, 10 MB), list, download, delete**; deleting detaches it from jobs.
- Bean Validation on all request DTOs; **uniform error body** with `fieldErrors`.
- Resume usage counts ("used in N applications").
- 14 MockMvc integration tests covering auth, job lifecycle, search/filter, stats, isolation, resumes.

### 3.2 Broken / incorrect

| # | Issue | Evidence |
| --- | --- | --- |
| B1 | **Resume list loads every document's bytes.** `ResumeService.list()` maps full entities, so `GET /api/resumes` pulls all `file_data` blobs out of the DB. Grows linearly with library size. | `ResumeService.java:85` uses `findByUserIdOrderByCreatedAtDesc`; entity carries `byte[] fileData` |
| B2 | **Search/filter/stats run in Java memory, not SQL.** Every request loads all of the user's jobs and filters in a stream. Blocks pagination and degrades with volume. | `JobService.java:50-56`, `97-99` |
| B3 | **"Applied" statistic is misleading.** It counts `total − wishlist`, so it includes offers, rejections, withdrawals and ghosted — while the UI labels it "Applied". | `JobService.java:101`; `Dashboard.tsx` stat card |
| B4 | **Dashboard "Recent Activity" is not activity.** It sorts by `updatedAt`, not by any real event log — no history exists to show. | `Dashboard.tsx` `recentJobs` |
| B5 | **`index.html` has no `<title>`, meta description, or favicon.** Browser tab and link previews are blank/default. | `jobhunt/index.html` |
| B6 | **`state.jobs` and `AllJobs`' local list can disagree.** AllJobs keeps its own filtered copy; a mutation made elsewhere refreshes the context but not that view until a filter changes. | `AllJobs.tsx` local `jobs` state |

### 3.3 Partial / stubs

| # | Item | Reality |
| --- | --- | --- |
| P1 | **Interviews** | `Interview` / `InterviewType` types exist in the frontend and are never used. No entity, table, endpoint, or UI. Not even referenced by `Job`. |
| P2 | **Kanban drag-and-drop** | Works, but reordering *within* a column is not persisted — order is always `updatedAt DESC`. Only the status change is saved. |
| P3 | **Salary** | A single free-text `salaryRange` string. Not filterable, sortable, or comparable. |
| P4 | **Status history** | Only `updatedAt` exists. No record of when a job entered each stage. |
| P5 | **"shadcn/ui"** | `components.json` is configured for the new-york style, but **zero shadcn components exist**. `components/ui/` holds two hand-rolled files (`Modal`, `StatusBadge`). |
| P6 | **Design tokens** | `tailwind.config.js` defines `primary`/`secondary`/`accent` hex colours and `darkMode: ['class']`, but **0 usages** of `bg-primary`/`text-primary`/etc. exist, and no CSS variables are defined. The UI hardcodes Tailwind palette (`indigo-500` ×51, `gray-*` ×~160). |
| P7 | **`sonner` toasts, `recharts` charts, `next-themes` dark mode, `zod` + `react-hook-form` validation** | All installed as dependencies, **never imported**. Features absent despite the stack being "declared". |

### 3.4 Missing (relative to your phase list)

**Phase 1 (core tracker):** tags, work mode, deadline, job source, salary structure, **sorting**,
**pagination**, **CSV export**, **job duplicate prevention**, status history, per-job detail view.

**Phase 2 (resumes & documents):** cover-letter storage, proper file-storage architecture
(currently blobs in Postgres), content/magic-byte file validation, storage abstraction.

**Phase 3 (AI):** entirely absent — matching, match score, skills gap, JD summarisation, interview
questions, STAR practice, cover-letter generation, learning recommendations, provider abstraction,
non-AI fallback.

**Phase 4 (analytics & UX):** funnel, time-series charts, conversion/offer rates over time,
date-range filters, upcoming interview reminders, deadline surfacing, company/role analytics,
toasts, confirmation dialogs, loading skeletons, dark/light theme, accessibility, keyboard navigation.

**Phase 5 (security & production):** refresh tokens, token revocation on logout, **rate limiting on
auth**, **Flyway migrations**, **Docker Compose for PostgreSQL**, **OpenAPI/Swagger**, production
profile, security headers, mandatory DB credentials outside dev, file-access authorisation review.

**Phase 6 (testing/CI):** no **CI at all**, no `@DataJpaTest`/`@WebMvcTest`/unit tests, no mocked-provider
tests, **no frontend tests whatsoever** (no Vitest/Jest installed).

**Phase 7 (docs/deployment):** no architecture diagram, no screenshots, no Swagger instructions, no
Docker instructions, no deployment guide, no demo-account instructions, no known-limitations section,
no `docs/` folder.

### 3.5 Duplication & dead code

| Item | Detail |
| --- | --- |
| **44 of 53 declared frontend dependencies are never imported** | Including `playwright`, `recharts`, `sonner`, `next-themes`, `zod`, `react-hook-form`, `date-fns`, `uuid`, and 26 Radix packages. Install time and attack surface for nothing. |
| `src/components/ui/StatusBadge.tsx` | Defined, **never used** (0 references). |
| `src/hooks/use-mobile.tsx` | Defined, **never used**. |
| `src/App.css` | Present, **never imported**. |
| Status colour styling | Duplicated inline in `AllJobs.tsx`, `Dashboard.tsx` and `StatusBadge.tsx` instead of one component. |
| Job status enum | Duplicated across backend `JobStatus` and frontend `JobStatus`, plus `STATUS_LABELS`, `STATUS_COLORS`, `KANBAN_COLUMNS` — four parallel lists to keep in sync. |

### 3.6 Security

**Solid:** BCrypt, JWT with mandatory ≥32-char secret, stateless sessions, ownership checks on every
resource, validation, consistent errors, no committed secrets, CORS restricted to the dev origin.

**Gaps:**

| # | Issue | Impact |
| --- | --- | --- |
| S1 | **JWT stored in `localStorage`** — readable by any injected script. | Token theft via XSS; no mitigation available in the browser. |
| S2 | **No rate limiting on `/api/auth/login` or `/register`.** | Unlimited credential stuffing / brute force. |
| S3 | **No refresh tokens and no logout revocation.** 24-hour access token; logout only clears the client. | A leaked token stays valid for its full lifetime. |
| S4 | **`DB_PASSWORD` defaults to `postgres`.** JWT is mandatory but DB credentials are not. | Weak default reaches production if overlooked. |
| S5 | File type validated by the client-supplied `Content-Type` header only. | A renamed non-PDF can be stored. |
| S6 | Resume bytes served with `Content-Disposition: attachment` (good) but **no content sniffing / antivirus hook**. | Uploaded malware distribution vector. |
| S7 | No security headers (HSTS, `X-Content-Type-Options`, CSP) and no `prod` profile. | Weaker defence in depth. |
| S8 | `dto/ErrorResponse` echoes `request.getRequestURI()`; harmless today, but error paths are not covered by a logging policy. | Minor. |

### 3.7 Database & migrations

- **No Flyway.** Schema is created by `ddl-auto: update` (`application.yml:20`). The words "Flyway" and
  "Liquibase" appear only inside a comment.
- No `db/migration` directory, no `V1__baseline.sql`.
- `ddl-auto: update` cannot drop/rename columns safely and produces drift between environments.
  **This blocks every later phase**, because each phase adds columns.
- Tests use H2 in PostgreSQL-compatibility mode; the PostgreSQL path has **still never been executed**.
- `byte[]` resumes live in the `resumes` table (mapped to `bytea`).

### 3.8 Frontend ↔ backend contract

- Contracts line up: `lib/api.ts` methods map 1:1 onto the controllers; enums use the same lowercase
  wire values; null fields are omitted server-side and consumed as `undefined`.
- `interviews?: Interview[]` in the frontend `Job` type is **never populated by the API** (P1).
- `outcome` is returned by the API but **not rendered anywhere** in the UI.

### 3.9 Responsive & accessibility

**Responsive:** reasonable — 25 responsive utility classes, a collapsible mobile sidebar with overlay,
`grid-cols-2 md:grid-cols-3 lg:grid-cols-5` stat cards, horizontally scrolling Kanban.

Weaknesses: 8 Kanban columns are painful on a phone with no column-switcher; the fixed 64-width sidebar
plus `lg:ml-64` needs a tablet check; long company names/URLs are truncated rather than wrapped.

**Accessibility: effectively none.**

| Signal | Count |
| --- | --- |
| `aria-*` | **0** |
| `role=` | **0** |
| `tabIndex` | **0** |
| `onKeyDown` / `onKeyPress` | **0** |
| `htmlFor` | 5 (auth forms only) |
| focus management (`focus()`, trap, autofocus) | **0** |

Concretely: job cards and job rows are `div`s with `onClick` — unreachable by keyboard; `Modal` has no
`role="dialog"`, no `aria-modal`, no focus trap and no focus restore; icon-only buttons have no
`aria-label`; colour-only status coding; no `prefers-reduced-motion` handling.

### 3.10 Tests, build, CI, deployment

- Backend: 14 tests, all `@SpringBootTest` + MockMvc. Good coverage of happy paths and isolation;
  **no unit tests, no `@DataJpaTest`, no `@WebMvcTest`, no mocked-provider tests**.
- Frontend: **zero tests**, no test runner installed, no config.
- **No `.github/workflows`** — CI is entirely absent, so nothing enforces "tests must pass".
- **No `Dockerfile`, no `docker-compose.yml`** — Postgres cannot be started locally with one command.
- No production profile, no deployment configuration, no hosting setup.

---

## 4. Gap matrix

| Your phase | State | Coverage |
| --- | --- | --- |
| 0 — Audit | ✅ done | this document |
| 1 — Core job tracker | 🟡 partial | CRUD·pipeline·search·filter·ownership·stats done. Missing: tags, work mode, deadline, source, sorting, pagination, CSV, duplicates, history |
| 2 — Resumes & documents | 🟡 partial | upload·download·delete·metadata·usage done. Missing: storage architecture, cover letters, deep validation |
| 3 — AI assistant | ❌ absent | nothing exists |
| 4 — Analytics & quality | 🟡 minimal | 5 stat cards + 2 rates. Missing: funnel, charts, ranges, reminders, toasts, skeletons, dark mode, a11y |
| 5 — Security & production | 🟡 partial | BCrypt·JWT·validation·ownership·CORS done. Missing: Flyway, refresh tokens, rate limiting, Docker, Swagger, prod profile |
| 6 — Testing & CI | 🟡 partial | 14 backend tests. Missing: CI, unit/repo/controller slices, all frontend tests |
| 7 — Docs & deployment | 🟡 partial | setup + API reference + limitations done. Missing: diagram, Swagger, Docker, deployment, screenshots |

---

## 5. Prioritized roadmap

Ordered by dependency and risk, not by phase number. Sizes are relative (S ≈ under an hour of
focused work, M ≈ a solid session, L ≈ several sessions).

### M0 — Foundation & hygiene · size S · risk low

Must land before any schema work.

1. **Flyway**: add `flyway-core` + `flyway-database-postgresql`, write `V1__baseline.sql` matching the
   current schema, set `ddl-auto: validate`. Tests keep H2 (Flyway runs there too).
2. **CI**: `.github/workflows/ci.yml` — backend `mvn -B verify`, frontend `npm ci && npm run build && npm run lint`.
   This makes "don't merge on red" real.
3. **Docker Compose** for local PostgreSQL, so the PostgreSQL path can finally be executed.
4. **OpenAPI/Swagger** via `springdoc-openapi-starter-webmvc-ui`, documented at `/swagger-ui.html`.
5. **Dead code**: drop the 44 unused dependencies, `StatusBadge.tsx`, `use-mobile.tsx`, `App.css`;
   fix the 2 lint warnings; add `<title>`/meta/favicon to `index.html`.
6. **Frontend test harness**: Vitest + Testing Library + jsdom, with 2–3 smoke tests so M1+ can add tests.

### M1 — Phase 1: complete the job tracker · size L · risk medium

1. **Schema** (via Flyway `V2`): `tags` (job↔tag many-to-many or text[]), `work_mode`, `deadline`,
   `job_source`, salary split (`salary_min`, `salary_max`, `currency`), `job_status_history` table.
2. **API**: server-side **pagination + sorting** (Spring `Pageable`), DB-level search/filter (replace the
   in-memory streams — fixes B2), **CSV export** endpoint, **duplicate detection** (same company + title +
   user → warn/confirm), status-history writes on every transition.
3. **Frontend**: rebuild `AllJobs` as a proper data table (sort headers, page controls, tag/work-mode
   filters, CSV export button); extend `JobForm` with the new fields + tag input; add a job detail view
   with a **status timeline**; surface deadlines.
4. **Dashboard**: fix the "Applied" semantics (B3) and replace the fake "Recent Activity" with real
   history (B4).
5. **Tests**: coverage for every new endpoint, filter, sort, page boundary, duplicate rule, and CSV shape.

### M2 — Phase 5: security & production · size M · risk medium

Deliberately ahead of AI/analytics — these are production blockers and cheap to do once.

1. **Refresh tokens** with rotation, hashed server-side, and **revocation on logout** (fixes S3).
2. Move the access token out of `localStorage` where practical (in-memory + httpOnly refresh cookie) (S1).
3. **Rate limiting** on auth endpoints (Bucket4j or a filter with a token bucket) (S2).
4. Make **`DB_PASSWORD` mandatory** outside the `dev` profile; add a `prod` profile (S4).
5. **Magic-byte file validation** and a documented AV hook point (S5, S6).
6. Security headers + a logging policy that provably excludes secrets/tokens (S7, S8).
7. Security-focused tests: expired/revoked tokens, refresh rotation, rate-limit 429, cross-user file access.

### M3 — Phase 2: resumes & documents · size M · risk medium

1. **`FileStorageService`** abstraction with a local-filesystem implementation (S3-compatible interface
   documented for later); migrate existing blobs with a Flyway-compatible backfill. Fixes B1/P7.
2. **Cover letters**: entity, endpoints, upload/link, UI, tests.
3. Stream downloads without loading blobs into memory; keep list responses metadata-only.
4. Resume ↔ job linking surface (which jobs use which resume, from the job side too).

### M4 — Phase 4: analytics & product quality · size L · risk low-medium

1. **Analytics**: funnel visualisation, weekly/monthly application charts, conversion/offer/response rates,
   company and role breakdowns, **date-range filters** (`recharts` is already installed).
2. **Reminders**: upcoming interviews and approaching deadlines (cron/notification surface).
3. **UX**: `sonner` toasts replacing `window.alert/prompt`, Radix `alert-dialog` replacing `window.confirm`,
   loading skeletons, real empty/error states, optimistic updates.
4. **Theming**: introduce CSS variables + token usage, then dark/light via `next-themes`.
5. **Accessibility**: `role`/`aria` on interactive elements, keyboard-navigable cards and rows, focus trap
   and restore in `Modal`, `aria-live` for async results, visible focus rings, reduced-motion support.
6. **Mobile**: Kanban column switcher, tablet breakpoint audit, wrap long text.

### M5 — Phase 3: AI career assistant · size L · risk medium-high

1. **Provider abstraction** in the backend (`AiClient` interface + configurable provider), keys supplied
   only via environment variables; **never** called from the browser.
2. **Resume ↔ job-description matching**: transparent score with an explicit breakdown (matched terms,
   weights), matched skills, missing skills, relevant projects — strictly from the user's own resume text.
3. **Honest suggestions** with explicit "we do not claim ATS scores" wording; no invented qualifications.
4. JD summarisation, interview-question generation, STAR practice, cover-letter generation, learning
   recommendations.
5. **Non-AI fallback** when no key is configured (clear setup path + deterministic keyword-overlap matching).
6. Loading/error/retry/empty states throughout; tests with a **mocked provider** (no live calls in CI).

### M6 — Phase 7: documentation & deployment · size M · risk low

1. Architecture diagram (rendered), feature list, screenshots, Swagger instructions, Docker instructions,
   deployment guide for a Java host + a static frontend host, security notes, known limitations,
   demo-account instructions.

### Continuous — Phase 6: testing

Tests are written **with** each milestone, not at the end: backend unit + `@DataJpaTest` + `@WebMvcTest`
slices, frontend component/form/Kanban/auth-flow/resume-flow tests, and CI gating every push.

---

## 6. Effort & risk summary

| Milestone | Size | Risk | Blocks |
| --- | --- | --- | --- |
| M0 Foundation | S | Low | everything (Flyway first) |
| M1 Core tracker | L | Medium | M4 analytics, M5 AI |
| M2 Security | M | Medium | deployment |
| M3 Resumes/documents | M | Medium | M5 cover letters |
| M4 Analytics & UX | L | Low-Med | — |
| M5 AI assistant | L | Med-High | needs M1 + M3 |
| M6 Docs & deployment | M | Low | — |

---

## 7. Recommended sequence

```
M0  →  M1  →  M2  →  M3  →  M4  →  M5  →  M6
```

Deliberate deviations from your phase order, with reasons:

- **Flyway and CI move into M0** instead of living in Phase 5. Every later phase alters the schema, and
  `ddl-auto: update` cannot evolve it safely. Doing Flyway first avoids re-baselining later.
- **Security (Phase 5) moves ahead of AI (Phase 3)** and ahead of resume storage. Refresh tokens,
  rate limiting and mandatory credentials are production blockers that get more expensive once more
  surface area exists.
- **Testing is continuous**, not a phase. Each milestone ships with its tests and CI enforces them.

## 8. Guardrails I will hold to

- No new framework; React + Vite stays, Java stays.
- No invented user data, jobs, skills, or experience — anywhere, including AI prompts and fixtures.
- No fake AI responses in a shipped feature; no guaranteed-ATS claims.
- No secrets in source, logs, or docs.
- No deletion of working features; every change is additive or a documented fix.
- Tests + build green at the end of every milestone; **no push without your approval**.
- I will not call anything "production-ready" while the PostgreSQL path remains unexecuted and
  a11y/storage/AI work is outstanding.
