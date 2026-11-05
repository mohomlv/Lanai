# Landata

Multi-tenant BI platform (Metabase-style): connect customer databases, build parameterized questions and dashboards, and generate SQL from natural language using schema docs stored as **SQL Server VECTOR** embeddings.

| Layer | Stack |
|---|---|
| Backend | Java 21 · Spring Boot 4.1 · Maven · Flyway · JobRunr · JSqlParser · Tink |
| App DB | SQL Server (VECTOR 1024) |
| Frontend | Next.js 15 · React 19 · TypeScript · TanStack Query · Monaco · ECharts |
| Local deps | Docker Compose: SQL Server, Redis, sample Postgres |

---

## Repository layout

```
landata/
├── src/main/java/com/landata/   # Spring modular monolith
│   ├── identity/                # auth, companies, users, platform admin
│   ├── datasource/              # connections, catalog sync, pools
│   ├── knowledge/               # scrape, schema_doc, embeddings
│   ├── query/                   # QueryExecutor, NativeSql, questions
│   ├── ai/                      # Ask AI / LLM orchestration
│   ├── dashboard/ · viz/ · admin/ · billing/ · shared/
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/            # Flyway
│   └── chart-templates/
├── web/                         # Next.js UI (see web/README.md)
├── docker-compose.yml
├── AGENTS.md                    # agent / contributor constraints
└── .cursor/rules/               # product specs (architecture, API, security)
```

---

## Prerequisites

- **JDK 21+**
- **Maven 3.9+** (or use `./mvnw` / `mvnw.cmd`)
- **Node.js 20+** and npm
- **Docker** (optional but recommended for local SQL Server / Redis)
- SQL Server that supports **VECTOR(1024)** for the app database

---

## Quick start

### 1. Infrastructure

```bash
docker compose up -d
```

Creates:

| Service | Port | Notes |
|---|---|---|
| SQL Server | `1433` | SA password `Landata_1234` (compose only) |
| Redis | `6379` | cache / rate limits |
| Customer Postgres | `5433` | sample datasource (`shop` / `shop`) |

Create the app database once (SQL Server):

```sql
CREATE DATABASE landata;
```

### 2. Backend API

Create your local config (not committed):

```bash
# Windows PowerShell
Copy-Item .env.example .env
Copy-Item src\main\resources\application.yml.example src\main\resources\application.yml
# then edit application.yml / .env with your DB URL, user, password, JWT secret
```

`src/main/resources/application.yml` is **gitignored** so your DB password and host never go to GitHub. Only `application.yml.example` is in the repo.

Run (API on **8081** so it matches the Next.js proxy):

```bash
# Windows
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"

# macOS / Linux
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

- Health: [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
- Swagger UI: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- OpenAPI: [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs)

Default Spring `server.port` in `application.yml` is `8080`; local UI expects **8081**.

### 3. Frontend

```bash
cd web
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

The Next.js app proxies `/api/*` to `LANDATA_API_URL` (default `http://localhost:8081`). See [web/README.md](web/README.md).

---

## Environment variables

| Variable | Purpose | Example |
|---|---|---|
| `LANDATA_DB_URL` | App JDBC URL | `jdbc:sqlserver://localhost:1433;databaseName=landata;encrypt=true;trustServerCertificate=true` |
| `LANDATA_DB_USER` | App DB user | `sa` |
| `LANDATA_DB_PASSWORD` | App DB password | (from compose / your server) |
| `LANDATA_JWT_SECRET` | JWT HMAC secret (≥ 32 bytes) | long random string |
| `LANDATA_KEYSET_PATH` | Tink keyset file | `./data/tink-keyset.json` |
| `CURSOR_API_KEY` | Optional Cursor Agents key | |
| `LANDATA_API_URL` | Frontend → API origin (Next rewrites) | `http://localhost:8081` |

Platform admins (all users / branch editing) are configured in `application.yml`:

```yaml
landata:
  platform-admin-emails:
    - you@example.com
  platform-admin-branch-codes: []
```

Do **not** commit real passwords, JWT secrets, or provider keys. Prefer env vars over editing committed defaults.

---

## First-run product flow

1. Sign up at `/signup`
2. Settings → verify email (dev may expose `dev_token`) → create a company
3. Datasources → connect a DB → Test → Sync catalog → Scrape docs
4. Settings → configure an AI provider / agent
5. Ask AI, or Queries → write native SQL → save as a question
6. Dashboards → assemble cards

### Native SQL parameters

- `{{name}}` — bound parameter (widgets appear above the editor)
- `[[ … ]]` — optional block; removed when every param/attribute inside is empty
- `@username` / `@branches` — user attributes from the JWT (`username`, `branch_ids` claims)

Example:

```sql
SELECT *
FROM Tbl_Branch
WHERE 1 = 1
[[AND id IN @branches]]
```

---

## Backend modules

| Module | Responsibility |
|---|---|
| `identity` | Signup/login, JWT, companies, memberships, usernames, `branch_ids`, platform users |
| `datasource` | Encrypted credentials, catalog sync, autocomplete, pools |
| `knowledge` | Scrape jobs, `schema_doc`, query guides, embeddings |
| `query` | Compile / validate / run; questions; parameter sources |
| `ai` | Conversations, retrieval, draft SQL, accept/discard |
| `dashboard` | Tabs, cards, dashboard parameters |
| `viz` | Chart templates and validation |
| `admin` | Audit, quotas, embed tokens |
| `billing` | Company quotas |
| `shared` | Security, crypto, API errors, properties |

Every customer statement runs only through **`QueryExecutor`** after JSqlParser safety checks. Agents never receive JDBC credentials.

---

## Frontend routes (`web/`)

| Path | Purpose |
|---|---|
| `/login`, `/signup` | Auth |
| `/ask` | Natural-language SQL |
| `/queries` | Native SQL builder |
| `/questions`, `/dashboards` | Saved content |
| `/datasources` | Connections + catalog + docs |
| `/settings` | Company + AI providers |
| `/profile` | Username + branch list |
| `/admin` | Company members / keys |
| `/users` | Platform admin: all users |

---

## Useful commands

```bash
# Backend tests
./mvnw test

# Backend package
./mvnw -DskipTests package

# Frontend production build
cd web && npm run build && npm start
```

---

## Specs for contributors

Start with [AGENTS.md](AGENTS.md). Detailed rules live under [`.cursor/rules/`](.cursor/rules/) (`technology.md`, `architecture.md`, `entities.md`, `security.md`, `api.md`, …).

---

## Security notes for GitHub

- Keep `.env`, `data/tink-keyset.json`, and real DB URLs out of git (see `.gitignore`).
- Override `landata.jwt.secret` in every non-local environment.
- Customer DB passwords are encrypted with Tink; never log them or put them in prompts.
- Rotate any credentials that were ever committed as local defaults before publishing a public repo.

---

## License

Proprietary / unlicensed unless you add a `LICENSE` file.
