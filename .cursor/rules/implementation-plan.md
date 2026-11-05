# Implementation Plan

Each phase ends in something demoable and has acceptance criteria (AC). Phases 1–3 deliver a useful non-AI BI tool; AI arrives early (phase 2) in the *documentation* role so retrieval can be tuned long before generation matters.

## Phase 0 — Foundation (≈ 1 week)
- Repo, Maven multi-module or single module with Spring Modulith, CI (build, tests, Trivy, gitleaks), Docker Compose (Postgres+pgvector, Redis, SQL Server, MySQL, Postgres "customer" fixture).
- Flyway baseline, base entity conventions, `TenantContext`, Problem Details, OpenAPI, structured logging with scrubber, Testcontainers base classes.
- Frontend skeleton (routing, API client, auth shell).
**AC:** `./mvnw verify` green in CI; Modulith verification test passes; a dummy tenant-scoped endpoint passes `CrossTenantIT`.

## Phase 1 — Identity, tenancy, datasources (≈ 2 weeks)
- Auth (signup/login/refresh/verify/reset), companies, memberships, invitations, role checks, company switcher.
- Tink crypto service, datasource CRUD with encrypted secret, `HostValidator`, JDBC allowlist, `DriverAdapter` for Postgres/SQL Server/MySQL, test-connection with read-only detection, pooling, audit log.
**AC:** two companies cannot see each other's data (automated); passwords never in responses/logs (scrubber test); SSRF test suite blocks metadata/loopback hosts; test connection works for all three dialects in Testcontainers.

## Phase 2 — Catalog and AI documentation (≈ 2–3 weeks)
- Catalog sync (metadata, FKs, inferred FKs, sampling per sharing level, fingerprint), catalog UI (hide tables/columns, PII flags).
- `AiAgentClient` + `CursorAgentClient` (create, runs, stream/poll, usage, archive/delete), WireMock contract tests, `ai_provider_config`.
- Scrape jobs (JobRunr), table-doc and overview prompts, validators, skeleton fallback, docs UI (view/edit/versions/diff).
- `EmbeddingClient` (local ONNX), chunker, `doc_embedding` + HNSW, `RetrievalService`.
- **Measure Cursor latency/cost here** and decide on the fallback provider.
**AC:** scrape of a 50-table sample DB finishes with ≥ 95% tables documented; docs reference only real columns (validator); retrieval recall@8 ≥ 90% on 30 hand-labelled questions; user-edited docs are never overwritten silently.

## Phase 3 — Query engine and builder (≈ 3–4 weeks)
- `Dialect`s, `SqlCompiler` (+ golden tests per dialect), `ParameterParser`, `QueryValidator` (JSqlParser + denylist), `QueryExecutor`, result cache, cancellation, `query_run`.
- Visual builder UI, SQL editor (Monaco + autocomplete from catalog), parameter widgets, saved questions + versions + collections.
**AC:** golden SQL tests pass for all dialects; fuzz suite (multi-statement, comments, forbidden functions) rejects all unsafe SQL; timeout/row-cap tests per dialect; optional blocks and multi-select work with bind limits.

## Phase 4 — Visualization (≈ 2 weeks)
- Chart template loader (from `docs/chart-templates/`), schema validation, auto-pick, ECharts builders for all 12 types, pivot service, viz settings UI.
**AC:** every template's example config validates against its schema (build check); auto-pick matches the rule table on fixtures; visual regression snapshots for each chart type.

## Phase 5 — Dashboards (≈ 2–3 weeks)
- Dashboard CRUD, tabs, grid layout, card types, parameters + mapping UI, parallel card execution with coalescing, caching, auto-refresh, URL-synced filters.
**AC:** one filter drives cards with differently named parameters; failing card does not break the page; 10-card dashboard < 5 s warm; URL round-trips filter state.

## Phase 6 — AI query assistant and dashboard drafts (≈ 3–4 weeks)
- Orchestrator state machine, SSE progress, quotas, context builder, `query-generate` / `query-repair` prompts, full validation pipeline, drafts + accept/discard, conversations/follow-ups, clarification flow, feedback.
- Dashboard planner (A/B/C stages).
- Golden set (≥ 30 cases) + `AiGoldenSetTest`; metrics dashboards.
**AC:** validity@final ≥ 90% and execution accuracy ≥ target agreed after phase 2 measurements on the golden set; zero hallucinated tables reach the UI (validator-enforced); quota and sharing-level rules enforced by tests.

## Phase 7 — Hardening and growth
Oracle adapter, subscriptions/email export, embed tokens, API keys, doc refiner, custom chart templates, MFA/SSO, billing, row-level permissions, maps, MCP catalog server, performance and load tests, security review/pen-test.

## Cross-cutting backlog (start early)
- Observability dashboards and alerts; retention jobs; backup/restore drill; key-rotation runbook; privacy policy text describing AI data flow; load test of pool manager with 100 datasources.

## Suggested first 10 tickets
1. Project skeleton + CI + Testcontainers base
2. `TenantContext` + Hibernate filter + `CrossTenantIT`
3. Flyway V1 (identity tables) + signup/login/refresh
4. Companies, memberships, invitations
5. Tink `SecretCipher` + datasource entity + write-only DTO
6. `HostValidator` + JDBC property allowlist + tests
7. `PostgresAdapter` + pool manager + test connection
8. `SqlServerAdapter`, `MySqlAdapter`
9. Catalog reader (Postgres first) + sync job
10. `CursorAgentClient` walking skeleton (create agent → run → poll result) against WireMock

## Decision log (fill as you go)
| Date | Decision | Why |
|---|---|---|
| 2026-10-20 | Embedding model/dimension | bge-m3, vector(1024), local ONNX. Change the column if a spike picks another model, before the migration ships |
| 2026-10-20 | Cursor vs direct LLM for interactive queries | Interface is Cursor; fallback must exist before the assistant is done. Measure latency in phase 2 |
| 2026-10-20 | Own SQL compiler vs jOOQ commercial | Own `SqlCompiler`. jOOQ OSS does not cover SQL Server or Oracle |
