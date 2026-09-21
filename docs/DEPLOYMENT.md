# Deployment

Two containers: a Spring Boot API and an nginx image that serves the SPA and proxies `/api`.
Keeping them on one origin means no CORS in production and the refresh cookie stays first-party.

## 1. Required configuration

The `prod` profile deliberately has **no defaults** for these. The application refuses to start
without them, which is what you want: a missing credential should be a failed deploy, not a silent
fallback to a development value.

| Variable | Required | Notes |
| --- | --- | --- |
| `JWT_SECRET` | **yes** | At least 32 characters. Generate with `openssl rand -base64 48`. Rotating it signs every user out. |
| `DB_URL` | **yes** | `jdbc:postgresql://host:5432/jobhunt` |
| `DB_USERNAME` | **yes** | |
| `DB_PASSWORD` | **yes** | |
| `CORS_ALLOWED_ORIGINS` | **yes** | Comma-separated origins. No default, so a misconfigured deploy fails closed. |
| `STORAGE_ROOT` | recommended | Where uploaded documents live. Mount a volume. |
| `SECURE_COOKIES` | recommended | `true` behind HTTPS. Set by the prod profile. |
| `AI_API_KEY` | optional | Without it, generation is disabled and matching still works. |

## 2. Docker Compose (single host)

```bash
export POSTGRES_PASSWORD='...'
export JWT_SECRET="$(openssl rand -base64 48)"
docker compose up -d --build
```

- App: <http://localhost:5173>
- API: <http://localhost:8080>
- Health: <http://localhost:8080/actuator/health>

Compose refuses to start without `POSTGRES_PASSWORD` and `JWT_SECRET`.

## 3. Managed platforms

Both images are self-contained, so any container host works.

**API (Railway / Render / Fly.io / ECS):**

1. Build from `backend/Dockerfile`.
2. Attach a PostgreSQL instance; copy its connection details into `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
3. Set `JWT_SECRET` and `CORS_ALLOWED_ORIGINS` (your frontend URL).
4. Mount a persistent volume at `/app/data/uploads` — otherwise uploaded documents are lost on
   redeploy.
5. Point the platform's health check at `/actuator/health`.

**Frontend (Vercel / Netlify / Cloudflare Pages) — static option:**

- Build command `npm run build`, output directory `dist`.
- Set `VITE_API_BASE_URL` to the API origin.
- Because the frontend and API are then on *different* origins, add the frontend origin to
  `CORS_ALLOWED_ORIGINS` and set `SECURE_COOKIES=true`. The refresh cookie is `SameSite=Lax`, which
  works for same-site requests but **not** cross-site ones — for a split deployment move the API
  behind the same domain (a path or subdomain proxy) or switch the refresh cookie to
  `SameSite=None; Secure`.

**Frontend (nginx image) — recommended:** one origin, no CORS, no cross-site cookie problems.

## 4. Database and migrations

Flyway runs on every start and applies anything outstanding. Hibernate only validates, so a schema
mismatch stops the deploy rather than mutating data.

Sharing a database between containers is unnecessary — migrations are applied once, and further
instances see the schema is current.

### Adopting an existing database

A database that predates Flyway has the tables but no `flyway_schema_history`. Baseline it once so
Flyway does not try to re-create what exists:

```bash
mvn flyway:baseline \
  -Dflyway.url="$DB_URL" -Dflyway.user="$DB_USERNAME" -Dflyway.password="$DB_PASSWORD" \
  -Dflyway.baselineVersion=1 -Dflyway.baselineDescription=baseline
```

Then start the API normally; later migrations apply on top.

## 5. Operational notes

- **Backups:** `pg_dump` the database *and* back up the uploads volume. A database backup alone
  restores resumes as metadata pointing at files that no longer exist.
- **Logs:** the API logs to stdout. It never logs request bodies, tokens or the AI key.
- **Scaling:** the API is stateless apart from the refresh-token table, so it scales horizontally —
  but uploads must move to shared storage (the `FileStorageService` interface exists for exactly
  this), and the in-process auth rate limiter should become shared state.
- **Rollback:** redeploy the previous image. Flyway migrations are forward-only by design; if a
  migration must be undone, write a new one rather than editing history.
- **First run:** there is no seed data and no default account. Register through the UI.

## 6. Post-deploy checklist

```bash
curl -fsS https://your-host/actuator/health          # {"status":"UP"}
curl -fsS -o /dev/null -w '%{http_code}\n' https://your-host/api/jobs   # 401 - auth is on
```

- [ ] `/actuator/health` returns `UP`
- [ ] `/api/jobs` without a token returns `401`
- [ ] `/swagger-ui.html` loads
- [ ] Register, sign in, create a job
- [ ] Upload a resume, then download it
- [ ] Sign out, sign back in without re-entering the password (refresh flow)
- [ ] Uploads volume is mounted and survives a restart
