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
| `npm run test` | Vitest in watch mode |
| `npm run test:run` | Vitest once (used by CI) |

## Tests

Unit tests use **Vitest** with **Testing Library** and jsdom. Configuration lives in
`vitest.config.ts` (separate from `vite.config.ts` because Vitest 2 is typed against Vite 5 while
this project uses Vite 6); the React plugin and the `@/` alias are merged in from the Vite config.

Tests live next to the code as `*.test.ts` / `*.test.tsx` under `src/`.

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
