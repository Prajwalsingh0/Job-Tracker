# Demo

## Run it locally

Two terminals, no containers required.

```bash
# 1. Database (or point DB_URL at any PostgreSQL instance)
export POSTGRES_PASSWORD='...'
export JWT_SECRET="$(openssl rand -base64 48)"
docker compose up -d postgres

# 2. API
cd backend
export DB_USERNAME=jobhunt DB_PASSWORD="$POSTGRES_PASSWORD" JWT_SECRET="$JWT_SECRET"
mvn spring-boot:run

# 3. Frontend
cd jobhunt
npm install
cp .env.example .env     # Windows: copy .env.example .env
npm run dev
```

Open <http://localhost:5173>.

There is **no seed data and no default account** — the app never ships invented demo records. Register
with any email you control; the password needs at least 6 characters.

## Walkthrough

| Step | What to look at |
| --- | --- |
| Register, then reload the page | The session survives without a stored access token — it is exchanged silently using the httpOnly refresh cookie |
| **All Jobs → Add Job** | The form captures company, title, status, work mode, source, deadline, salary, tags and an optional resume |
| Try adding the same company **and** title twice | `409` — the duplicate guard stops the same role being tracked twice |
| Open a job, change its status, save, reopen it | The **status history** timeline records each transition (a no-op change adds nothing) |
| Drag a card between columns on **Pipeline** | The move persists; the board has a stage switcher on narrow screens |
| **All Jobs → sort a column / page through results** | Sorting, search and paging all run in the database |
| **All Jobs → Export CSV** | Downloads the current filtered set as RFC 4180 CSV with a UTF-8 BOM |
| **Analytics** | Funnel, monthly trend from real history, company/role tables, upcoming deadlines, active interviews; switch the range preset |
| **Resumes → upload a PDF** | Rejected unless the bytes really are a PDF; version tag is typed up front |
| **Resumes → Cover Letters** | Create one from pasted text or an uploaded document, linked to a job |
| **Assistant** | Paste experience text and analyse the match. Without `AI_API_KEY` the status chip says "not configured" and the generation buttons stay disabled with an explanation |
| Sign out, then sign back in | The refresh token is revoked; the cookie is cleared |
| <http://localhost:8080/swagger-ui.html> | The full API, with **Authorize** to paste a token and call protected endpoints |

## Screenshots

Not included. They would have to be captured from a running instance in a browser, which this
environment could not provide, and mocked-up images would be worse than none. Here is how to capture
your own:

```bash
# with the stack running, capture at desktop and mobile widths
#   desktop: 1440 x 900
#   mobile:   390 x 844
```

Pages worth capturing: Dashboard, Analytics, Pipeline, All Jobs, Resumes & Cover Letters, Assistant.
Save them under `docs/screenshots/` and reference them from the README.

## Trying the AI features

```bash
export AI_API_KEY='sk-...'
export AI_MODEL='gpt-4o-mini'                      # or any model your endpoint serves
export AI_BASE_URL='https://api.openai.com/v1'     # any OpenAI-compatible endpoint
```

Restart the API and the Assistant's status chip switches to the provider name. Generation requests are
built only from the job you saved and the text you pasted.
