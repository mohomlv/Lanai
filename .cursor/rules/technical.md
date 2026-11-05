Product overview. Library versions, module boundaries, and the jOOQ decision live in `technology.md` (that file wins). Table DDL lives in `entities.md`. AI behavior lives in `ai-agents.md`.

Here's a brief you can paste to the planning agent. It's a plain document, so you can copy it as is.

---

# Product Brief: Self-Hosted BI Platform with AI Query Assistant (Metabase-like)

## 1. Goal
A multi-tenant BI platform where users connect their own databases, build queries and charts, assemble parameterized dashboards, and ask an AI assistant to generate queries from natural language. It is built from scratch on Spring Boot, not as a Metabase fork.

## 2. Tech stack (proposed)
- **Backend:** Java 21, Spring Boot 4.1.x, Spring Security (JWT), Spring Data JPA (app DB only), an in-house `SqlCompiler` (visual queries), JSqlParser (SQL validation), HikariCP (per-datasource pools), JobRunr (scrape and sync), Tink (envelope encryption).
- **App DB:** PostgreSQL 16+ with pgvector 0.8+.
- **Frontend:** React + TypeScript + Vite, TanStack Query, ECharts, react-grid-layout, Monaco.
- **Async:** JobRunr. Redis for query result caching and rate limits.
- **AI agent:** Cursor Cloud Agents API, wrapped behind an `AiAgentClient` interface, with a direct-LLM fallback for interactive queries. Embeddings are local ONNX, not Cursor.

## 3. Core modules

**3.1 Tenancy and auth**
- Public sign-up and login.
- A user can create companies and belong to several. They pick the active company after login (company switcher).
- Roles per company: owner, admin, editor, viewer. All data (datasources, queries, dashboards) is scoped by `company_id`.

**3.2 Datasource connections**
- Supported: PostgreSQL, SQL Server, MySQL/MariaDB, Oracle (pluggable `DriverAdapter` per type).
- The user supplies host, port, database, user, password, SSL options, and a free-text description of the database (business context, important tables, glossary).
- Credentials are encrypted at rest (Tink AES-GCM envelope encryption; KEK from env or a KMS) and never returned by the API.
- A "test connection" action. Each datasource gets a pooled, read-only connection.

**3.3 Schema sync and AI-generated docs**
- JDBC `DatabaseMetaData` collects tables, columns, types, PK, FK, and indexes into catalog tables.
- A **scrape job** (async) sends an agent the user's description plus the catalog and sampled column values. The agent returns one **markdown doc per table**: purpose, columns, relationships and join hints, example questions. A database-level overview doc is generated as well.
- Docs are chunked and embedded into pgvector with metadata (`company_id`, `datasource_id`, `table`, `doc_version`). Users can view and edit the docs, and editing re-embeds them.
- Re-sync is triggered manually or on a schedule. A schema fingerprint is used to re-scrape only what changed.
- **Security:** the agent must never receive credentials. It gets only metadata and limited samples, with a per-column "exclude from samples" flag for PII.

**3.4 Query builder**
- **Visual builder:** pick table, columns, filters, group by, aggregations (count/sum/avg/min/max), sort, limit, and joins suggested from FKs. Output is an internal `QuerySpec` JSON compiled to SQL per dialect by an in-house `SqlCompiler`.
- **SQL editor:** native SQL with syntax highlighting.
- **Parameters** (both modes):
  - Variable syntax like `{{start_date}}` or `[[AND status = {{status}}]]` for optional clauses.
  - Types: text, number, date, date range, single select, multi select. A select can be backed by a static list or by a query.
  - Parameters are always bound as JDBC prepared-statement parameters, never string-concatenated.
- **Execution guardrails:** SELECT only (validated with JSqlParser), a read-only DB user, statement timeout, max rows, and result caching.
- Saved questions with versioning.

**3.5 AI query assistant**
- The user types a request and picks a datasource.
- Pipeline: embed the request, retrieve the top-k table docs plus the datasource description from pgvector, build a prompt, send it to the agent, and receive native SQL plus parameters, a chart template, and viz config. Accepted AI questions open in the SQL editor.
- Validate the output (parse, check tables and columns against the catalog, dry run with `LIMIT`) before showing it. On a validation or SQL error, auto-retry up to 2 times with the error message fed back.
- The user can accept the result, edit it, or reply with a follow-up ("add a filter by month") to produce a new version.
- **Template files:** one markdown file per chart type, stored in the repo (for example `/chart-templates/bar.md`). Each describes required and optional fields, the data shape, and an example config. They are injected into the prompt when the request involves a chart.

**3.6 Visualizations**
- Types: table, number/KPI, line, area, bar, stacked bar, row, pie/donut, scatter, combo, pivot table, gauge, funnel, map (later).
- Chart settings: x/y mapping, series, colors, axis labels, number formatting, goal lines.
- Default chart is auto-picked from result column types, and the user can override it.

**3.7 Dashboards**
- Grid layout with cards (questions, text, headings), tabs, and drag/resize.
- **Dashboard parameters** (date, text, select, and so on) are mapped to each card's query parameters. One filter can drive several cards, each with its own field mapping.
- Parameter values are carried in the URL for shareable links.
- Auto-refresh, subscriptions and email export (later), and public or embedded links with signed tokens (later).

**3.8 Admin and operations**
- Audit log: who ran which query on which datasource.
- Per-company quotas for AI requests and query rate.
- Metrics and tracing (Micrometer).

## 4. Data model (main tables)
`user`, `company`, `membership(role)`, `datasource(encrypted_credentials, description)`, `catalog_table/column/fk`, `schema_doc(markdown, version)`, `doc_embedding(vector)`, `scrape_job`, `saved_question(query_spec, params, viz_config)`, `question_version`, `dashboard`, `dashboard_card(param_mappings)`, `ai_request(prompt, retrieved_doc_ids, result, status)`, `audit_log`.

## 5. Cursor agent integration
- Auth with an API key, stored encrypted per company or per platform.
- Flow: `POST /v1/agents` to start, then stream via SSE or poll `GET /v1/agents/{id}/runs/{runId}`. Follow-ups go through `POST /v1/agents/{id}/runs`, with only one active run per agent.
- Store `agent_id`, `run_id`, status, result, duration, and token usage (`GET /v1/agents/{id}/usage`) per request for billing and debugging.
- Use a `QueryGenerator` interface with a Cursor implementation, so a direct LLM API implementation can replace it without touching the rest of the system.

## 6. Risks to design around
1. **Latency and cost:** Cursor cloud agents are built for code tasks on a repo, so runs may take far longer than an interactive query needs. Scraping is fine as a background job, but for query generation measure it early. Have a fallback (cache, streaming progress UI, or a direct LLM implementation) ready.
2. **Security:** only SELECT, read-only DB accounts, no credentials to the agent, and strict `company_id` isolation in retrieval filters.
3. **Beta API:** the v1 Cloud Agents API is in public beta, so isolate it behind the interface.
4. **SQL correctness:** always validate against the real catalog before execution.

## 7. Suggested phases
1. Auth, companies, datasource CRUD with encrypted credentials, test connection.
2. Schema sync, catalog, and the Cursor scrape job producing docs in pgvector.
3. Query builder (visual and SQL) with parameters, execution guardrails, and saved questions.
4. Visualizations with the chart templates.
5. Dashboards with parameter mapping.
6. AI query assistant (retrieval, generation, validation, retry, follow-ups).
7. Audit, quotas, subscriptions, embedding.

---

I can turn any phase into a detailed implementation plan, for example the `QuerySpec` JSON schema and the jOOQ compiler, or the parameter-mapping model for dashboards.