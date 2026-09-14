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

## 1. Set up the database

Create the database and a role (adjust names/passwords as you like):

```sql
CREATE DATABASE jobhunt;
CREATE USER jobhunt WITH PASSWORD 'change-me';
GRANT ALL PRIVILEGES ON DATABASE jobhunt TO jobhunt;
```

The schema is created automatically on first start (`spring.jpa.hibernate.ddl-auto: update`).
No manual DDL is required.

---

## 2. Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API starts on **http://localhost:8080**.

Configuration is read from environment variables, with sensible local defaults:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/jobhunt` | JDBC URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `JWT_SECRET` | dev-only value | HMAC signing key, **must be ≥ 32 characters** |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Browser origins allowed to call the API |
| `SERVER_PORT` | `8080` | HTTP port |

Example:

```bash
cd backend
DB_USERNAME=jobhunt DB_PASSWORD=change-me JWT_SECRET="a-long-random-secret-at-least-32-chars" mvn spring-boot:run
```

### Run the packaged jar

```bash
cd backend
mvn package
java -jar target/jobhunt-backend-1.0.0.jar
```

---

## 3. Run the frontend

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

## 4. Tests and builds

```bash
# Backend: 14 integration tests (auth + job CRUD + isolation + resumes)
cd backend && mvn test

# Backend: run tests and build the jar
cd backend && mvn package

# Frontend: type-check and production build
cd jobhunt && npm run build
```

Backend tests run against an **in-memory H2 database** in PostgreSQL compatibility mode
(`src/test/resources/application-test.yml`), so `mvn test` needs no database server.
The running application always uses PostgreSQL.

---

## API overview

Base URL: `http://localhost:8080/api`. All endpoints except register/login require
`Authorization: Bearer <token>`.

### Authentication

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Create an account, returns a JWT |
| `POST` | `/api/auth/login` | Exchange credentials for a JWT |
| `GET` | `/api/auth/me` | Current user profile |

### Jobs

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/jobs?search=&status=` | List your jobs, optionally searched/filtered |
| `GET` | `/api/jobs/{id}` | Fetch one job |
| `POST` | `/api/jobs` | Create a job |
| `PUT` | `/api/jobs/{id}` | Update a job |
| `PATCH` | `/api/jobs/{id}/status` | Move a job to another pipeline stage |
| `DELETE` | `/api/jobs/{id}` | Delete a job |
| `GET` | `/api/jobs/stats` | Dashboard metrics |

Job statuses: `wishlist`, `applied`, `phone_screen`, `interview`, `offer`, `rejected`,
`withdrawn`, `ghosted`.

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
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Ada","email":"ada@example.com","password":"secret123"}'

# Create a job (paste the token from the response above)
curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
  -d '{"companyName":"Acme","jobTitle":"Backend Engineer","status":"applied"}'

# Dashboard stats
curl http://localhost:8080/api/jobs/stats -H "Authorization: Bearer <TOKEN>"
```

---

## Known limitations

- **PostgreSQL was not executed in this environment.** No PostgreSQL server or Docker was
  available, so the schema and queries were verified against H2 in PostgreSQL-compatibility
  mode. Please run the backend once against a real PostgreSQL instance before relying on it.
- Interview tracking exists in the frontend types but has no backend table or endpoint yet.
- Access tokens are stored in `localStorage`; there is no refresh-token flow or logout
  revocation (the token simply expires).
- `ddl-auto: update` is convenient for development. Use Flyway or Liquibase before running
  this in production.
- Resume list endpoints load the document bytes with the entity; for large libraries move
  the files to object storage and keep only references in the database.
- The `uuid` npm packages are no longer used by the frontend (ids come from the database).
