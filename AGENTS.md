# Landata

Multi-tenant BI app (Metabase-like): connect customer databases, build parameterized questions and dashboards, and generate SQL from natural language using schema docs stored as SQL Server VECTOR embeddings.

Read specs in `.cursor/rules/` before planning or coding. When two files disagree, use this order:

1. `technology.md` — versions, libraries, module boundaries
2. `architecture.md` — package layout (api / application / infrastructure)
3. `entities.md` — tables (wins over `entity.md`)
4. `security.md` — credentials, SQL safety, tenancy
5. `ai-agents.md` — scrape and query-generation behavior
6. `query-spec-and-parameters.md`, `api.md`, `implementation-plan.md`
7. `entity.md` and `technical.md` — overview only; both contain older decisions

Do not give Cursor agents database credentials, JDBC URLs, or a SQL tool. The app reads metadata and samples; the agent only writes docs or SQL text. Every generated statement goes through `QueryExecutor` before it runs.
