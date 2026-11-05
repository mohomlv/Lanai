# Technology

Source of truth for versions, libraries, and boundaries. `technical.md` is the product overview; if it names a different library, follow this file.

## 1. Runtime

| Piece | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot **4.1.x** (4.1.1 or the current 4.1 patch; do not start on 4.2 milestones) |
| Build | Maven, one deployable app. Use Spring Modulith only to enforce package boundaries, not as a microservice split |
| Package layout | Modular layered monolith: `api` / `application` / `infrastructure.*` per feature module — see `architecture.md` |
| App database | SQL Server 2025+ with native **VECTOR(1024)** (cosine `VECTOR_DISTANCE`, filter `company_id` + `datasource_id` in the same query) |
| Customer databases | PostgreSQL, SQL Server, MySQL/MariaDB in v1. Oracle is a later adapter; do not offer it in the UI until that adapter exists |
| Cache / rate limit | Redis (Lettuce via Spring Data Redis) |
| Background work | **JobRunr** (scrape, sync, retention, key rotation). No Kafka in v1 |
| Local dev | Docker Compose: app SQL Server 2025, Redis, and throwaway Postgres / SQL Server / MySQL as "customer" databases. Testcontainers in CI |

## 2. Backend libraries

| Job | Library | Why |
|---|---|---|
| HTTP API | `spring-boot-starter-webmvc` | JSON API. Long work returns `202` plus SSE or polling. No request thread waits on a Cursor run |
| Auth | Spring Security, JWT access tokens (15 min) + hashed rotating refresh tokens | Resource server. `company_id`, `role`, `username`, and `branch_ids` are claims |
| App persistence | Spring Data JPA + Hibernate filter on `company_id` | App tables only |
| Migrations | Flyway | `entities.md` is the model. Do not let Hibernate `ddl-auto` create tables |
| Customer SQL execution | JDBC + HikariCP, one pool per datasource, evict idle pools | The only way a customer statement runs |
| SQL validation | **JSqlParser** (Apache-2.0) | Single statement, SELECT/WITH only, function denylist. Parse failure rejects the query |
| Visual SQL compile | **In-house `SqlCompiler`**, one `Dialect` per database | `QuerySpec` is a small closed language. The compiler emits quoted identifiers and `?` binds |
| Secrets | **Google Tink** AES-GCM envelope encryption | KEK from env in dev, KMS or Vault in production. Store `secret_key_id` |
| Passwords | Argon2id | `user_account.password_hash` |
| API docs | springdoc-openapi | Problem Details (`application/problem+json`) for errors |
| Jobs | `jobrunr-spring-boot-starter` | Retries, dashboard, schedule for schema sync |
| Embeddings | ONNX Runtime (Java) in-process | Cursor does not return embeddings. The vector store is ours |
| HTTP to Cursor | JDK `HttpClient` behind `AiAgentClient` | WireMock contract tests. A feature flag swaps in `DirectLlmClient` |
| Fallback LLM | Spring AI as the body of `DirectLlmClient` only | The orchestrator, retrieval, and validators stay ours |
| Tests | JUnit 5, Testcontainers, WireMock, Spring Modulith verify | `CrossTenantIT` is required for every controller |

### Do not adopt these for v1

- **jOOQ Open Source Edition** for customer SQL. Its Apache build covers PostgreSQL and MySQL. SQL Server and Oracle require a commercial jOOQ license (Express is not enough for full Oracle; Professional is the edition that covers SQL Server and Oracle). Dynamic customer schemas also get little from jOOQ code generation. Revisit only if a license is purchased, and then use the runtime DSL, not generated tables.
- **Hibernate / JPA entities mapped to customer databases.** Schemas are unknown until sync.
- **A raw JDBC URL from the user.** `DriverAdapter.buildJdbcUrl(type, host, port, database, ssl)` plus an allowlist of JDBC properties (`security.md`).
- **String concatenation of parameter values.** Visual and native queries both bind with `PreparedStatement`.
- **Kafka, Elasticsearch, a separate vector database.** SQL Server VECTOR is the app vector store.

## 3. Process boundaries

```
browser
  → API (auth, company scope)
      → CatalogSync          JDBC metadata only
      → QueryExecutor        the only SQL runner
      → ScrapeOrchestrator   builds prompts from catalog + samples
      → QueryOrchestrator    retrieval → agent → validators → draft
          → AiAgentClient    Cursor Cloud Agents, no repo, no tools
          → EmbeddingClient  local ONNX, not Cursor
      → app SQL Server       entities, docs, vectors, ciphertext
```

Customer credentials and the Cursor API key exist only inside the crypto service and the connection/provider clients. They are not prompt fields, agent `envVars`, logs, or `query_run` rows.

`AiAgentClient` methods: create (optional client `agentId` of the form `bc-<uuid>`), start run, stream with resume, poll, usage, cancel, archive, delete. A repeated create with the same id returns **409 `agent_id_conflict`**. Treat that as "load the existing agent", not as a failed product operation. `envVars` cannot be combined with a client-supplied `agentId`; this product does not send env vars.

Cursor Cloud Agents API v1 is public beta. No-repo agents must be enabled on the account, and the key must be an unrestricted user or service-account key (a repo-scoped key cannot create them).

## 4. Two SQL paths

| Path | Stored as | Produced by |
|---|---|---|
| Visual builder | `question_version.query_spec` + `parameters` | User, compiled by `SqlCompiler` |
| Native / AI | `question_version.native_sql` with `{{param}}` and `[[optional]]` | User or the query assistant |

The assistant returns **native SQL**, not `QuerySpec` (`ai-agents.md` contract). Accepted AI questions open in the SQL editor. A later project can add a SQL-to-`QuerySpec` parser; v1 does not need one.

`question_version.parameters` is the stored parameter list. The copy inside the `QuerySpec` example in `query-spec-and-parameters.md` is illustrative. The compiler reads the column, and the spec JSON does not keep a second list.

## 5. Frontend

| Piece | Choice |
|---|---|
| App | React 19, TypeScript, Vite |
| Server state | TanStack Query |
| Routing | React Router. Dashboard filter values live in the query string |
| Charts | Apache ECharts. One builder per `chart_template.key` |
| Dashboard grid | `react-grid-layout`, 24 columns, as in `entities.md` |
| SQL editor | Monaco, autocomplete from the catalog |
| UI primitives | One small set (Radix primitives + project CSS). Do not add a second component library |

Chart templates are markdown plus JSON Schema in the repo, loaded into `chart_template` at startup. Paths in older notes say `docs/chart-templates/` and `classpath:chart-templates/`. Use **`src/main/resources/chart-templates/`** only.

## 6. Embeddings and retrieval

- Model is chosen **before** the embedding migration. Recommended default: `BAAI/bge-m3` (1024 dimensions, multilingual) exported to ONNX. `entities.md` already says `vector(1024)`; change that column in the same migration if the model changes. Do not guess a dimension later.
- Call the model **outside** the database transaction. Then, in one short transaction, delete the previous chunks for that doc and insert the new ones.
- Every search filters `company_id` and `datasource_id` in the same `VECTOR_DISTANCE` query. Do not search all tenants and filter afterward.
- Chunk text and `catalog_column.sample_values` are plaintext. Default `ai_sharing_level` is `schema_only`. Samples are written only for `schema_and_samples`, never for PII or `exclude_from_samples`.

## 7. Cursor usage (what it is good at here)

| Work | How | Expected wait |
|---|---|---|
| Table docs and database overview | Background scrape job. One no-repo agent handles a small batch of tables, then it is deleted | Minutes per table is acceptable |
| Interactive question | Same API, but measure p50/p95 in phase 2 before building the UI around it. Cloud agents boot a VM and are often too slow for a chat box | Must have `DirectLlmClient` ready |
| Dashboard plan | Several question generations. Cap parallelism (the spec says 3) | Background, draft until the user accepts |

Prompts are stateless: each run carries the full context. Do not rely on the agent remembering earlier turns. Long-lived agents also slow the stream, because the API loads the whole history before it forwards events. Prefer a new agent per scrape batch and per query conversation, and delete it when the conversation is idle (`ai-agents.md` §10).

## 8. Guardrails that are easy to implement wrong

- **Read-only session is not enough.** PostgreSQL honors `default_transaction_read_only`. SQL Server does not have an equivalent session switch. Require a dedicated read-only database user, and still reject non-SELECT with JSqlParser.
- **SSRF.** Validate host, port, and resolved address (`security.md`). Some JDBC drivers resolve DNS again, so a pinned IP in our check can be bypassed by the driver. Prefer putting the resolved IP into the JDBC URL when the dialect allows it, and re-check on every pool create.
- **Optional SQL blocks** (`[[ ... ]]`) must not leave a bare `WHERE` or a dangling `AND`/`OR`. The parser removes the block and then cleans the surrounding keyword.
- **`date_range`** is inclusive start, exclusive end (`end + 1 day` for date columns), computed in the user's timezone. Timestamp columns still need an explicit zone in the doc; do not assume the database zone matches the user.
- **Identifier case.** The compiler always quotes catalog identifiers. Dialect notes that say "quote only when needed" are for the AI prompt and will produce wrong SQL on mixed-case PostgreSQL. The validator must resolve names against the catalog's exact case.
- **Filter dropdowns** (`SELECT DISTINCT`) always have `LIMIT`/`TOP`/`FETCH` (max 500) plus the datasource statement timeout.
- **`metric_definition.expression_sql`** is validated with the same SELECT-only pipeline before save. It is data inside the prompt, and it is never concatenated into SQL by the compiler.
- **Query cache key** includes `company_id`, `datasource_id`, `schema_fingerprint`, sql hash, and params hash.
- **Soft-deleted datasources** must not block reuse of the same name. Unique indexes on name include `WHERE deleted_at IS NULL` (the DDL in `entities.md` does not yet).

## 9. Permissions the DDL leaves open

- `datasource_permission.role` has admin, editor, viewer. **Owner** is a membership role and must be treated as admin for datasource checks, or the owner cannot query.
- A missing permission row means **deny** for native SQL and **allow query** for viewer and above on that company's own datasource. Write the rule in one `DatasourceAuthorizer` and test it. Do not scatter defaults.
- `GET /datasources/{id}/values` is used by dashboard filters. Viewers run dashboards, so this endpoint is viewer-accessible, contrary to the `E` mark in `api.md`.

## 10. Repo layout for files the other docs already name

These paths are the ones to create. Older docs mention `docs/prompts/`, `templates/`, and `classpath:prompts/` interchangeably.

```
src/main/resources/prompts/            query-generate, query-repair, scrape-table-doc, scrape-db-overview, dashboard-plan, dialect-notes
src/main/resources/chart-templates/    one markdown file per chart type + viz schema
src/main/resources/db/migration/       Flyway
src/test/resources/ai-golden/          later, phase 6
```

`schema-doc-template.md` and `db-overview-template.md` in this rules folder are the contracts the validators enforce. Copy their bodies into the prompt resources when the prompts are written. They are not written yet.

## 11. Decisions already made here

| Topic | Decision |
|---|---|
| jOOQ vs own compiler | Own `SqlCompiler` until a commercial jOOQ license exists |
| Job runner | JobRunr |
| Encryption | Tink envelope, not a single AES key with a hand-rolled IV |
| AI result shape | Native SQL + parameters + viz. Not `QuerySpec` |
| Interactive AI | Cursor client behind an interface, with a direct-LLM fallback before the feature is called done |
| Embedding | Local ONNX, bge-m3 unless a spike picks another 1024-d multilingual model |
| Vector DB | SQL Server VECTOR(1024) in the app database |

## 12. Still open (do not invent silently)

- Exact ONNX export and max sequence length for bge-m3 on the chosen hardware.
- Cursor p95 latency and cost on a 50-table scrape (phase 2 exit criterion in `implementation-plan.md`). If p95 for one table doc is unacceptable, scrape stays on Cursor only if a batch prompt (several tables per run) brings the cost down; otherwise scrape uses `DirectLlmClient` too.
- Per-company Cursor key versus one platform key. Both fit `ai_provider_config`. Default the product to a platform key with a quota, and allow a company key as an override.
