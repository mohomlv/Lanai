# Landata web

Next.js 15 (App Router) UI for the Landata BI API.

## Stack

- React 19 + TypeScript
- TanStack Query
- Monaco SQL editor (catalog + `@username` / `@branches` autocomplete)
- Apache ECharts
- `react-grid-layout` for dashboards
- Radix primitives + project CSS

## Setup

1. Start the Spring API (see root [README.md](../README.md)). Default proxy target: `http://localhost:8081`.
2. From this folder:

```bash
cp .env.example .env.local   # optional
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

### Environment

| Variable | Default | Purpose |
|---|---|---|
| `LANDATA_API_URL` | `http://localhost:8081` | Rewrite target for `/api/:path*` (`next.config.ts`) |

```bash
# example: API on another host
LANDATA_API_URL=http://127.0.0.1:8081 npm run dev
```

### Scripts

| Script | Description |
|---|---|
| `npm run dev` | Turbopack dev server on port 3000 |
| `npm run build` | Production build |
| `npm start` | Serve production build |
| `npm run lint` | ESLint |

## App map

| Area | Routes |
|---|---|
| Auth | `/login`, `/signup`, `/forgot-password`, `/reset-password` |
| Ask AI | `/ask` |
| SQL | `/queries`, `/questions/[id]` |
| Dashboards | `/dashboards`, `/dashboards/[id]` |
| Data | `/datasources`, `/datasources/[id]`, knowledge docs |
| Account | `/settings`, `/profile` |
| Admin | `/admin` (company), `/users` (platform admin) |

## Typical flow

1. Sign up → Settings → create company  
2. Datasources → connect → Test → Sync catalog → Scrape docs  
3. Settings → AI provider / agents  
4. Ask AI or Queries → save question → Dashboards  

SQL tips: type `{{name}}` for parameters, `@` for user attributes, wrap optional filters in `[[ ]]`.
