# JobHunt Frontend

React 18 + TypeScript + Vite single-page app for the JobHunt job-application tracker.
UI is Tailwind CSS with shadcn/ui-style components.

## Setup

```bash
npm install
cp .env.example .env      # Windows: copy .env.example .env
npm run dev               # http://localhost:5173
```

The app needs the Spring Boot API from `../backend` running (see the root `README.md`).

| Script | Purpose |
| --- | --- |
| `npm run dev` | Dev server with HMR |
| `npm run build` | Type-check (`tsc -b`) and production build |
| `npm run preview` | Serve the production build |
| `npm run lint` | ESLint |

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Base URL of the backend API |

## How it talks to the backend

- `src/lib/api.ts` — single HTTP client: attaches the bearer token, normalises error
  responses into `ApiError`, and exposes one typed method per endpoint.
- `src/context/AuthContext.tsx` — register/login/logout, restores the session from the
  stored JWT on load and clears it on sign-out.
- `src/context/JobContext.tsx` — owns jobs, resumes and dashboard stats; every mutation
  calls the API and re-syncs from the server.

Screens: Login, Register, Dashboard (stats from `/api/jobs/stats`), Pipeline (drag-and-drop
Kanban), All Jobs (server-side search and status filter), Resume Library (upload, preview,
download, delete).
