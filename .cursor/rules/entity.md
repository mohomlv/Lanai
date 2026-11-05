# Data Model Explained

Narrative overview. `entities.md` is the DDL source of truth. Encryption is Tink envelope (`security.md`), not a single hand-rolled AES key. Cursor ids live on `ai_agent` / `ai_run`, not on `scrape_job`. The notes after "Key design decisions" are design history; they are already folded into `entities.md`.

The entities fall into 6 groups. Everything business-related carries `company_id`, which is how tenant isolation works.

```
user ──< membership >── company
                          │
                          ├──< datasource ──< catalog_table ──< catalog_column
                          │        │                │               └─ catalog_fk
                          │        ├──< scrape_job  └──< schema_doc ──< doc_embedding
                          │        │
                          ├──< saved_question ──< question_version
                          │        │
                          ├──< dashboard ──< dashboard_card >── saved_question
                          │        └──< dashboard_parameter
                          │
                          └──< ai_request, audit_log
```

## 1. Identity and tenancy

| Entity | Key fields | Why it exists |
|---|---|---|
| `user` | id, email, password_hash (or external id if you use Keycloak), name | A person. Not tied to any single company. |
| `company` | id, name, slug, owner_id, created_at | The tenant. All data hangs off it. |
| `membership` | user_id, company_id, role (owner/admin/editor/viewer) | Many-to-many join. This is what lets one user belong to several companies and switch between them. Permissions are checked against this row. |

The active company is a claim in the JWT or session, and every query filters by it.

## 2. Datasources (the databases users connect)

| Entity | Key fields | Why it exists |
|---|---|---|
| `datasource` | id, company_id, name, type (postgres/sqlserver/mysql/oracle), host, port, db_name, username, **password_encrypted**, ssl_config (json), **description** (user context), status, last_synced_at, schema_fingerprint | One connection per customer database. The `description` is what users write to explain their business, and it feeds the AI. The fingerprint (a hash of the schema) tells you whether a re-sync found changes. |

Credentials: encrypt with AES-GCM (key from env or KMS), store the IV with the ciphertext, and never return the password from any API.

## 3. Schema catalog (the raw structure)

This is what the visual query builder uses, and it's the input to the AI docs.

| Entity | Key fields | Why it exists |
|---|---|---|
| `catalog_table` | id, datasource_id, schema_name, table_name, table_type (table/view), row_estimate, visible (bool), user_note | One row per table. `visible` lets admins hide tables from the builder and the AI. |
| `catalog_column` | id, table_id, name, data_type, normalized_type (text/number/date/bool), is_nullable, is_pk, is_pii, exclude_from_samples, sample_values (json), distinct_count | One row per column. `normalized_type` drives parameter types and default charts. `is_pii` and `exclude_from_samples` make sure those values never reach the agent. |
| `catalog_fk` | id, from_column_id, to_column_id, constraint_name | Foreign keys. They power join suggestions in the query builder and "how do I join these tables" hints in the AI docs. |

## 4. AI knowledge (docs and vectors)

| Entity | Key fields | Why it exists |
|---|---|---|
| `scrape_job` | id, datasource_id, status (queued/running/done/failed), cursor_agent_id, cursor_run_id, started_at, finished_at, error, token_usage | Tracks each async scrape. It maps your job to the Cursor agent and run, and keeps the usage for cost tracking. |
| `schema_doc` | id, datasource_id, table_id (null for the DB-level overview), markdown, version, source (ai/user_edited), is_current, created_at | The markdown doc per table (or per database). Versioned, so a user edit or a re-scrape creates a new version and keeps the old one. `is_current` marks the active one. |
| `doc_embedding` | id, doc_id, chunk_index, chunk_text, **embedding vector(N)**, company_id, datasource_id, table_name | The chunks used for retrieval. `company_id` and `datasource_id` are duplicated here on purpose so the vector search can filter on them directly without a join. |

Re-embed when a doc changes, and delete the old chunks in the same transaction.

## 5. Questions (queries and charts)

| Entity | Key fields | Why it exists |
|---|---|---|
| `saved_question` | id, company_id, datasource_id, name, description, created_by, collection_id, current_version_id | The "card". It's the identity of a saved query plus its chart. |
| `question_version` | id, question_id, version_no, mode (visual/native), **query_spec** (json) or native_sql, **parameters** (json), **viz_config** (json), created_by, created_at | The actual content, immutable per version, so you get history and rollback. |

`query_spec` is your internal JSON for visual queries (source table, columns, joins, filters, aggregations, group by, sort, limit). It's compiled to SQL per dialect.

`parameters` is an array like:
```json
[{"name":"start_date","type":"date","required":true,"default":"2026-01-01"},
 {"name":"status","type":"select_multi","options_source":{"kind":"static","values":["paid","open"]}}]
```

`viz_config` holds the chart type and settings:
```json
{"type":"line","x":"month","y":["revenue"],"colors":[],"number_format":"currency"}
```

Optional: `collection` (folders, with parent_id) to organize questions and dashboards.

## 6. Dashboards

| Entity | Key fields | Why it exists |
|---|---|---|
| `dashboard` | id, company_id, name, description, layout_settings, auto_refresh_seconds | The container. |
| `dashboard_card` | id, dashboard_id, question_id, tab_id, x, y, width, height, card_type (question/text/heading), text_content, **param_mappings** (json) | One tile on the grid. Position and size live here, and so does the parameter wiring. |
| `dashboard_parameter` | id, dashboard_id, name, type, default, label, order | The filters shown at the top of the dashboard. |

`param_mappings` is the key part. It connects a dashboard filter to a question's own parameter:
```json
[{"dashboard_param":"date_range","question_param":"start_date_range"},
 {"dashboard_param":"region","question_param":"region_filter"}]
```
That is how one dashboard filter drives several cards, even when each question names its parameter differently.

## 7. Operations

| Entity | Key fields | Why it exists |
|---|---|---|
| `ai_request` | id, company_id, user_id, datasource_id, prompt, retrieved_doc_ids (json), generated_spec (json/sql), validation_status, retry_count, cursor_agent_id, cursor_run_id, duration_ms, token_usage, resulting_question_id | One row per AI query request. It's your debugging record: you can see exactly which docs the model got and what it returned. It's also the base for quotas and billing. |
| `audit_log` | id, company_id, user_id, action (query_run, datasource_created, ...), datasource_id, question_id, sql_hash, row_count, duration_ms, created_at | Security and compliance trail of who ran what. |
| `company_quota` | company_id, ai_requests_per_month, queries_per_minute, max_rows | Per-tenant limits (optional at first). |

## Key design decisions

1. **`company_id` everywhere, even on child tables.** It makes row-level filtering simple and safe, and you can enforce it in one place (a Hibernate filter or a repository base class).
2. **Versioning for questions and docs.** Both change often and both are hard to debug without history.
3. **JSON columns for flexible parts** (`query_spec`, `parameters`, `viz_config`, `param_mappings`). Use Postgres `jsonb`. These shapes will evolve as you add chart types and parameter kinds, so you don't want a migration for each change.
4. **Catalog vs docs are separate.** The catalog is the exact structure, collected by code. The docs are the AI's interpretation, which can be wrong or edited. Validation of generated SQL should always check against the catalog, not the docs.
5. **Credentials live only in `datasource`.** Nothing else (jobs, requests, logs) stores or receives them.

You're right on all three. My first draft had no chart template table, no AI-for-dashboards path, and the Cursor ids were scattered across `scrape_job` and `ai_request`, which defeats the "swap provider" goal. Here are the fixes.

## 1. Chart templates

Use two layers:
- **Files in the repo** (`/chart-templates/bar.md`, `line.md`, ...). They are versioned with your code and reviewed in PRs.
- **A `chart_template` table.** The app loads the files into it at startup, and companies can later add custom ones.

| Entity | Key fields | Purpose |
|---|---|---|
| `chart_template` | id, company_id (**null = system template**), key (`bar`), name, version, description, **markdown** (what the AI reads), **viz_schema** (JSON Schema for `viz_config`), data_requirements (json: e.g. 1 dimension + 1..n measures), example_viz_config, example_sql, is_active | The single source the AI and the validator both use. |
| `chart_template_embedding` (optional) | template_id, embedding | Only if you have many templates and want to retrieve them by intent. With about 15 chart types, just include the relevant ones in the prompt. |

How the AI uses it:
1. Retrieve the table docs (vector search) and pick candidate chart templates, either all of them or the ones matching the request ("trend" → line, "share" → pie).
2. Put the chosen `markdown` into the prompt.
3. The AI returns `viz_config`, and you **validate it against `viz_schema`**. Invalid output goes back to the AI as an error for a retry.
4. Check `data_requirements` against the actual result columns, for example a line chart needs one date/number dimension.

## 2. AI for dashboards

The AI should also be able to say "build me a sales dashboard". Make `ai_request` generic, with a target:

| Entity | Key fields | Purpose |
|---|---|---|
| `ai_request` (changed) | ..., **target_type** (question/dashboard/doc_scrape), **plan** (json), status, resulting_question_id, resulting_dashboard_id | For a dashboard request, the AI first returns a plan: a list of questions (spec + chart each), a layout, and dashboard parameters with mappings. |
| `ai_request_artifact` | request_id, artifact_type (question/dashboard/card), artifact_id, status (draft/accepted/discarded) | Everything the AI created. Items stay **draft** until the user accepts, so nothing is saved by surprise. |
| `dashboard_template` (optional) | id, company_id, key, name, markdown, layout_json | Dashboard templates ("sales overview") the AI can start from, mirroring chart templates. |

Flow: user prompt → plan → create N draft questions and 1 draft dashboard → user reviews and accepts, or asks for changes.

## 3. AI agent tables (provider-agnostic)

Provider ids live in generic tables, so the Cursor-specific fields are isolated in one place.

| Entity | Key fields | Purpose |
|---|---|---|
| `ai_provider_config` | id, company_id (null = platform default), provider (`cursor`), **api_key_encrypted**, default_model_id, model_params (json), settings (json), is_active | Credentials and defaults. Encrypt the key like datasource passwords. |
| `ai_conversation` | id, company_id, user_id, datasource_id, target_type, title, status | A chat thread. It holds the follow-ups ("add a filter by month"). |
| `ai_message` | id, conversation_id, role (user/assistant/system), content, generated_spec (json), validation_status, created_at | Each turn. |
| `ai_agent` | id, company_id, provider, **provider_agent_id** (`bc-…`), purpose (scrape/query_assistant), conversation_id or scrape_job_id, status, created_at | Our record of a provider agent. |
| `ai_run` | id, agent_id, **provider_run_id**, request_id, prompt, status (CREATING/RUNNING/FINISHED/ERROR/CANCELLED/EXPIRED), result, error, duration_ms, started_at | One run. |
| `ai_usage` | run_id, company_id, input_tokens, output_tokens, cache_read_tokens, cache_write_tokens, total_tokens, cost_estimate | For quotas and billing. |

Changes to the earlier tables:
- Remove `cursor_agent_id`, `cursor_run_id` and token fields from `scrape_job` and `ai_request`. They get `ai_run_id` instead.
- **One agent per conversation.** A follow-up is `POST /v1/agents/{id}/runs` on the same agent, which keeps context. Only one run can be active per agent, so queue follow-ups per conversation. For scraping, one agent per scrape job.
- Create agents without repos (the docs say you can omit `repos` and `env` to get a no-repo agent), since you don't need a git repo here.

## 4. What else was missing

**Likely needed for a first usable version:**
- `collection` (folders with parent_id and permissions) for questions and dashboards, plus `collection_id` on both.
- `dashboard_tab`. I referenced `tab_id` on cards without a table.
- `invitation` (email, company, role, token, expires_at). Without it users can't join companies.
- `datasource_permission` (which roles or groups can query which datasource or tables). Otherwise every member can query everything.
- `query_run` (question_id or ad hoc, params hash, row_count, duration, status). It's separate from `audit_log` and is needed for performance and caching analysis.
- `query_cache` (key = datasource + sql + params hash, result ref, expires_at). Or store in Redis only.
- `field_filter` / dropdown value sources for parameters (for example "values of customers.country"), with a cached value list.
- `schema_sync_schedule` (cron per datasource), since I only mentioned scheduling.

**Valuable soon after:**
- `ai_feedback` (thumbs up/down and corrected SQL per message). It's the best source for improving the docs and prompts.
- `glossary_term` / `metric_definition` per datasource ("active customer" = ...). Inject it into the AI prompt and it improves accuracy more than almost anything else.
- `api_key` (per company, for programmatic access) and `embed_token` (signed public or embedded dashboards).
- `subscription` (dashboard → recipients, schedule, format) plus `notification_channel`.
- `datasource_connection_options` (SSH tunnel, allowed IP, max pool size, statement timeout). Customer DBs often sit behind tunnels.
- `file_asset` (logos, exported PDFs) if you export.
- `billing_plan` / `company_subscription` if this will be a paid SaaS.

**Cross-cutting, not tables:**
- Soft delete (`deleted_at`) and `created_at/updated_at/created_by` on every table.
- Flyway or Liquibase for migrations.
- Row-level `company_id` enforcement in one place (Hibernate filter), plus tests that prove cross-tenant access fails.
- Outbox or Kafka events for scrape completion if you want live UI updates.

## Updated relationship sketch

```
company ─┬─ ai_provider_config
         ├─ datasource ─┬─ catalog_* ─ schema_doc ─ doc_embedding
         │              ├─ glossary_term / metric_definition
         │              └─ scrape_job ──┐
         ├─ chart_template (null = system)
         ├─ ai_conversation ─ ai_message
         │        └─ ai_agent ─ ai_run ─ ai_usage   ←── scrape_job, ai_request
         ├─ ai_request ─ ai_request_artifact → question / dashboard (draft)
         ├─ collection ─ saved_question / dashboard ─ dashboard_tab ─ dashboard_card
         └─ invitation, api_key, query_run, query_cache, audit_log
```

I can now write the Postgres DDL for the complete model, or just the AI-related part (`chart_template`, `ai_*`) so you can start with that.