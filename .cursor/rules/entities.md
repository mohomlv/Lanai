# Data Model — Entities

SQL Server 2025+ with native `VECTOR(1024)`. Migrations via Flyway (`V1__…sql`). This document is the source of truth for table design.

## 0. Conventions
- PK: `id uuid primary key default gen_random_uuid()`.
- Timestamps: `created_at`, `updated_at` as `timestamptz not null default now()`.
- Soft delete: `deleted_at` (or `archived_at` for user content). Queries exclude soft-deleted rows by default.
- Tenant column: `company_id uuid not null references company(id)` on **every** business table, including children (denormalized on purpose for filtering and vector search).
- JSON: `jsonb`; shapes documented in §10 and validated with JSON Schema on write.
- Secrets: `bytea` ciphertext + `key_id text` (Tink envelope). Never plain text.
- Enums are `text` + `check` constraints (easier migrations than PG enums).

## 1. Relationship overview
```
user_account ──< membership >── company
company ──< invitation, api_key, collection, datasource, ai_provider_config, ai_conversation, ai_request, audit_log
datasource ──< catalog_table ──< catalog_column ; catalog_fk (column→column)
datasource ──< scrape_job ──< scrape_job_item ──> ai_run
datasource ──< schema_doc ──< doc_embedding ; glossary_term ; metric_definition
collection ──< saved_question ──< question_version ; dashboard ──< dashboard_tab ──< dashboard_card >── saved_question
saved_question ──< role_parameter_question
dashboard ──< dashboard_parameter
ai_conversation ──< ai_message ; ai_conversation ──< ai_agent ──< ai_run ──1 ai_usage
ai_request ──< ai_request_artifact → (saved_question | dashboard)
chart_template (company_id null = system)
```

## 2. Identity and tenancy
```sql
create extension if not exists vector;
create extension if not exists citext;

create table user_account (
  id uuid primary key default gen_random_uuid(),
  email citext not null unique,
  password_hash text,                       -- null if external IdP
  display_name text not null,
  username text unique,                     -- assigned on signup if missing; used as @username
  email_verified boolean not null default false,
  status text not null default 'active' check (status in ('active','disabled')),
  last_login_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table company (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  slug text not null unique,
  owner_id uuid not null references user_account(id),
  plan text not null default 'free',
  status text not null default 'active' check (status in ('active','suspended','deleted')),
  settings jsonb not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table membership (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  user_id uuid not null references user_account(id),
  role text not null check (role in ('owner','admin','editor','viewer')),
  status text not null default 'active' check (status in ('active','suspended')),
  branch_ids jsonb not null default '[]',   -- integer branch ids for @branches
  created_at timestamptz not null default now(),
  unique (company_id, user_id)
);

create table invitation (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  email citext not null,
  role text not null check (role in ('admin','editor','viewer')),
  token_hash text not null unique,
  invited_by uuid not null references user_account(id),
  status text not null default 'pending' check (status in ('pending','accepted','revoked','expired')),
  expires_at timestamptz not null,
  accepted_at timestamptz,
  created_at timestamptz not null default now()
);

create table api_key (                       -- phase 7
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  name text not null,
  key_prefix text not null,                  -- first chars, shown in UI
  key_hash text not null unique,             -- SHA-256 of full key
  scopes text[] not null default '{}',
  created_by uuid not null references user_account(id),
  last_used_at timestamptz, expires_at timestamptz, revoked_at timestamptz,
  created_at timestamptz not null default now()
);
```
Refresh tokens: `refresh_token(id, user_id, token_hash, family_id, expires_at, revoked_at, created_at)` (rotation with reuse detection).

## 3. Datasources and catalog
```sql
create table datasource (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  name text not null,
  type text not null check (type in ('postgres','sqlserver','mysql','mariadb','oracle')),
  host text not null,
  port int not null,
  database_name text not null,
  username text not null,
  secret_ciphertext bytea not null,          -- encrypted password
  secret_key_id text not null,
  ssl_mode text not null default 'require',
  options jsonb not null default '{}',       -- allowlisted JDBC props, pool size, timeouts, ssh tunnel
  description text,                          -- owner's business context (feeds AI)
  ai_sharing_level text not null default 'schema_only'
     check (ai_sharing_level in ('none','schema_only','schema_and_samples')),
  query_timeout_seconds int not null default 30,
  max_rows int not null default 10000,
  status text not null default 'new' check (status in ('new','connected','error','disabled')),
  last_test_at timestamptz, last_test_error text,
  last_synced_at timestamptz,
  schema_fingerprint text,
  server_version text,
  created_by uuid references user_account(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (company_id, name)
);

create table datasource_permission (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  datasource_id uuid not null references datasource(id),
  role text not null check (role in ('admin','editor','viewer')),
  can_query boolean not null default true,
  can_native_sql boolean not null default false,
  can_manage boolean not null default false,
  unique (datasource_id, role)
);

create table catalog_table (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  datasource_id uuid not null references datasource(id),
  schema_name text not null,
  table_name text not null,
  table_type text not null default 'table' check (table_type in ('table','view','materialized_view')),
  row_estimate bigint,
  display_name text,
  user_note text,
  visible boolean not null default true,
  removed_at timestamptz,                    -- set when no longer in source
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (datasource_id, schema_name, table_name)
);

create table catalog_column (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  table_id uuid not null references catalog_table(id) on delete cascade,
  name text not null,
  ordinal int not null,
  data_type text not null,                   -- native type
  normalized_type text not null check (normalized_type in ('text','integer','decimal','boolean','date','datetime','time','json','binary','other')),
  semantic_type text,                        -- id,name,email,currency,percent,url,category,created_at,...
  is_nullable boolean not null default true,
  is_pk boolean not null default false,
  is_pii boolean not null default false,
  exclude_from_samples boolean not null default false,
  visible boolean not null default true,
  display_name text,
  user_note text,
  sample_values jsonb,                       -- only if ai_sharing_level allows and not PII
  distinct_count bigint, null_fraction numeric(5,4),
  unique (table_id, name)
);

create table catalog_fk (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  datasource_id uuid not null references datasource(id),
  constraint_name text not null,
  ordinal int not null default 1,            -- composite FK support
  from_column_id uuid not null references catalog_column(id) on delete cascade,
  to_column_id uuid not null references catalog_column(id) on delete cascade,
  is_inferred boolean not null default false, -- guessed from naming (customer_id → customers.id), user-confirmable
  unique (datasource_id, constraint_name, ordinal)
);

create table schema_sync_schedule (
  datasource_id uuid primary key references datasource(id),
  company_id uuid not null references company(id),
  cron text not null default '0 3 * * *',
  enabled boolean not null default false,
  last_run_at timestamptz, next_run_at timestamptz
);
```

## 4. Knowledge (AI docs, vectors, glossary)
```sql
create table scrape_job (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  datasource_id uuid not null references datasource(id),
  status text not null default 'queued' check (status in ('queued','running','done','partial','failed','cancelled')),
  trigger text not null check (trigger in ('manual','schedule','schema_change','first_connect')),
  scope jsonb,                               -- null = all visible tables, or {"table_ids":[...]}
  total_items int not null default 0, done_items int not null default 0, failed_items int not null default 0,
  requested_by uuid references user_account(id),
  error text,
  started_at timestamptz, finished_at timestamptz,
  created_at timestamptz not null default now()
);

create table scrape_job_item (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  job_id uuid not null references scrape_job(id) on delete cascade,
  kind text not null check (kind in ('table','overview')),
  table_id uuid references catalog_table(id),
  status text not null default 'queued' check (status in ('queued','running','done','failed','skipped')),
  attempts int not null default 0,
  error text,
  updated_at timestamptz not null default now()
);

create table schema_doc (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  datasource_id uuid not null references datasource(id),
  table_id uuid references catalog_table(id),          -- null for overview/glossary docs
  kind text not null check (kind in ('table','database_overview','query_guide')),
  title text,                                          -- display name for user-written query guides
  markdown text not null,
  content_hash text not null,
  version int not null,
  source text not null check (source in ('ai','user')),
  is_current boolean not null default true,
  review_status text not null default 'accepted'
     check (review_status in ('accepted','proposed','rejected','superseded')),  -- AI output replacing a user-edited doc is 'proposed' until approved
  ai_run_id uuid,                                      -- fk added after ai_run exists
  created_by uuid references user_account(id),
  created_at timestamptz not null default now()
);
create unique index schema_doc_current_uq on schema_doc (datasource_id, coalesce(table_id,'00000000-0000-0000-0000-000000000000'::uuid), kind) where is_current and kind in ('table','database_overview');

create table doc_embedding (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  datasource_id uuid not null references datasource(id),
  doc_id uuid not null references schema_doc(id) on delete cascade,
  chunk_index int not null,
  chunk_kind text not null check (chunk_kind in ('header','columns','relationships','examples','overview','query_guide')),
  chunk_text text not null,
  token_count int,
  table_name text,                                     -- schema.table, for filtering/boosting
  embedding vector(1024) not null,                     -- dimension = embedding model
  created_at timestamptz not null default now()
);
create index doc_embedding_hnsw on doc_embedding using hnsw (embedding vector_cosine_ops);
create index doc_embedding_scope on doc_embedding (company_id, datasource_id);

create table glossary_term (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  datasource_id uuid references datasource(id),        -- null = company-wide
  term text not null, definition text not null,
  synonyms text[] not null default '{}',
  created_by uuid references user_account(id),
  created_at timestamptz not null default now()
);

create table metric_definition (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  datasource_id uuid not null references datasource(id),
  name text not null, description text,
  expression_sql text not null,                        -- e.g. SUM(o.total) FILTER (WHERE o.status='paid')
  base_table_id uuid references catalog_table(id),
  format text,
  created_by uuid references user_account(id),
  created_at timestamptz not null default now()
);
```
Embedding invariants: when a `schema_doc` gets a new current version, delete old chunks and insert new ones in the **same transaction**. Chunks: `header` (name, purpose, grain, tags), `columns` (groups of ~15 columns), `relationships`, `examples`.

## 5. Visualization templates
```sql
create table chart_template (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references company(id),              -- null = system template
  key text not null,                                   -- bar, line, ...
  name text not null,
  version int not null default 1,
  description text,
  markdown text not null,                              -- what the AI reads
  viz_schema jsonb not null,                           -- JSON Schema for viz_config
  data_requirements jsonb not null,
  example_viz_config jsonb, example_sql text,
  source text not null default 'file' check (source in ('file','custom')),
  content_hash text not null,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index chart_template_uq on chart_template (coalesce(company_id,'00000000-0000-0000-0000-000000000000'::uuid), key, version);

create table dashboard_template (                      -- phase 7
  id uuid primary key default gen_random_uuid(),
  company_id uuid references company(id),
  key text not null, name text not null,
  markdown text not null, layout jsonb not null,
  is_active boolean not null default true
);
```
System templates are loaded from `classpath:chart-templates/*` at startup; changed `content_hash` → new version row.

## 6. Content (questions, dashboards)
```sql
create table collection (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  parent_id uuid references collection(id),
  name text not null, description text,
  created_by uuid references user_account(id),
  created_at timestamptz not null default now(),
  archived_at timestamptz
);
create table collection_permission (                   -- phase 2 of sharing
  collection_id uuid references collection(id), company_id uuid references company(id),
  role text, level text check (level in ('view','edit')), primary key (collection_id, role)
);

create table saved_question (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  datasource_id uuid not null references datasource(id),
  collection_id uuid references collection(id),
  name text not null, description text,
  is_parameter boolean not null default false,            -- reusable source for other parameters
  parameter_fields jsonb,                                 -- {"fields":["city_id","city_name"],"value_field":"city_id","display_field":"city_name"}
  current_version_id uuid,                              -- fk added after question_version
  created_by uuid references user_account(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  archived_at timestamptz
);

create table question_version (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  question_id uuid not null references saved_question(id),
  version_no int not null,
  mode text not null check (mode in ('visual','native')),
  query_spec jsonb,                                     -- visual mode (see query-spec doc)
  native_sql text,                                      -- native mode with {{param}} / [[ ]]
  parameters jsonb not null default '[]',
  viz_template_key text not null default 'table',
  viz_config jsonb not null default '{}',
  result_metadata jsonb,                                -- column names/types from last run
  change_note text,
  source text not null default 'user' check (source in ('user','ai')),
  ai_request_id uuid,
  created_by uuid references user_account(id),
  created_at timestamptz not null default now(),
  unique (question_id, version_no),
  check ((mode='visual' and query_spec is not null) or (mode='native' and native_sql is not null))
);
alter table saved_question add foreign key (current_version_id) references question_version(id);

create table parameter_value_cache (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  datasource_id uuid not null references datasource(id),
  source_hash text not null,                            -- hash of column ref or query
  "values" jsonb not null,
  refreshed_at timestamptz not null default now(),
  unique (datasource_id, source_hash)
);

create table role_parameter_question (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  role text not null check (role in ('owner','admin','editor','viewer')),
  question_id uuid not null references saved_question(id),
  created_at timestamptz not null default now(),
  unique (company_id, role, question_id)
);

create table dashboard (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  collection_id uuid references collection(id),
  name text not null, description text,
  auto_refresh_seconds int,
  settings jsonb not null default '{}',
  created_by uuid references user_account(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  archived_at timestamptz
);
create table dashboard_tab (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  dashboard_id uuid not null references dashboard(id) on delete cascade,
  name text not null, position int not null default 0
);
create table dashboard_card (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  dashboard_id uuid not null references dashboard(id) on delete cascade,
  tab_id uuid references dashboard_tab(id) on delete cascade,
  card_type text not null check (card_type in ('question','text','heading')),
  question_id uuid references saved_question(id),
  text_content text,
  x int not null, y int not null, w int not null, h int not null,       -- 24-column grid
  viz_overrides jsonb not null default '{}',
  param_mappings jsonb not null default '[]',
  check ((card_type='question' and question_id is not null) or card_type<>'question')
);
create table dashboard_parameter (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  dashboard_id uuid not null references dashboard(id) on delete cascade,
  slug text not null, label text not null,
  type text not null check (type in ('text','number','date','date_range','select','select_multi','boolean')),
  default_value jsonb, required boolean not null default false,
  options_source jsonb,                                   -- {"kind":"static","values":[...]} | {"kind":"column","column_id":"..."} | {"kind":"query","question_id":"..."}
  position int not null default 0,
  unique (dashboard_id, slug)
);
```

## 7. Query execution log
```sql
create table query_run (                                  -- partition by month when large
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  user_id uuid references user_account(id),
  datasource_id uuid not null references datasource(id),
  question_id uuid, dashboard_id uuid, ai_request_id uuid,
  source text not null check (source in ('editor','card','dashboard','ai_validation','api','embed')),
  sql_hash text not null, params_hash text,
  compiled_sql text,                                      -- optional, configurable; never contains bound values
  status text not null check (status in ('success','error','timeout','cancelled','rejected')),
  row_count int, duration_ms int, cache_hit boolean not null default false,
  error_code text, error_message text,
  started_at timestamptz not null default now()
);
create index query_run_company_time on query_run (company_id, started_at desc);
```
**Result cache** lives in Redis (not a table). Key: `qc:{company_id}:{datasource_id}:{sql_hash}:{params_hash}`; TTL per datasource (default 5 min); value = compressed rows + column metadata.

## 8. AI
```sql
create table ai_provider_config (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references company(id),                  -- null = platform default
  provider text not null check (provider in ('cursor','direct_llm')),
  api_key_ciphertext bytea not null, api_key_key_id text not null,
  default_model_id text,
  model_params jsonb not null default '[]',
  settings jsonb not null default '{}',                    -- timeouts, concurrency, mcp
  is_active boolean not null default true,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table ai_conversation (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  user_id uuid not null references user_account(id),
  datasource_id uuid references datasource(id),
  target_type text not null check (target_type in ('question','dashboard')),
  title text,
  status text not null default 'open' check (status in ('open','closed')),
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table ai_message (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  conversation_id uuid not null references ai_conversation(id) on delete cascade,
  role text not null check (role in ('user','assistant','system')),
  content text not null,
  generated jsonb,                                         -- parsed agent output for assistant messages
  validation_status text check (validation_status in ('valid','repaired','invalid','clarification','n/a')),
  created_at timestamptz not null default now()
);

create table ai_agent (                                    -- our record of a provider agent
  id uuid primary key,                                     -- we mint this; provider id derived: 'bc-' || id
  company_id uuid not null references company(id),
  provider text not null,
  provider_agent_id text not null unique,                  -- e.g. bc-<uuid> (client-supplied for idempotent create)
  purpose text not null check (purpose in ('scrape','query_assistant','dashboard_planner','doc_refiner')),
  conversation_id uuid references ai_conversation(id),
  scrape_job_id uuid references scrape_job(id),
  status text not null default 'creating' check (status in ('creating','active','idle','archived','deleted','failed')),
  model_id text,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table ai_request (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  user_id uuid not null references user_account(id),
  conversation_id uuid references ai_conversation(id),
  datasource_id uuid references datasource(id),
  target_type text not null check (target_type in ('question','dashboard')),
  status text not null default 'queued' check (status in
    ('queued','retrieving','generating','validating','repairing','ready','needs_clarification','failed','accepted','discarded')),
  user_prompt text not null,
  retrieval jsonb,                                         -- [{doc_id, table, score}], glossary ids, metrics
  plan jsonb,                                              -- dashboard plan
  result jsonb,                                            -- final validated draft
  validation jsonb,                                        -- issues per attempt
  retry_count int not null default 0,
  error_code text, error_message text,
  created_at timestamptz not null default now(), finished_at timestamptz
);

create table ai_run (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  agent_id uuid not null references ai_agent(id),
  provider_run_id text,                                    -- run-<uuid>
  kind text not null check (kind in ('scrape_table','scrape_overview','generate_query','repair_query','plan_dashboard','refine_doc')),
  ai_request_id uuid references ai_request(id),
  scrape_job_item_id uuid references scrape_job_item(id),
  prompt_template text not null, prompt_version text not null,
  prompt_text text,                                        -- may be nulled by retention policy
  prompt_hash text not null,
  status text not null default 'creating'
    check (status in ('creating','running','finished','error','cancelled','expired','timeout')),
  result_text text, parsed_result jsonb,
  error_code text, error_message text,
  duration_ms int, started_at timestamptz, finished_at timestamptz,
  created_at timestamptz not null default now()
);
alter table schema_doc add foreign key (ai_run_id) references ai_run(id);

create table ai_usage (
  run_id uuid primary key references ai_run(id) on delete cascade,
  company_id uuid not null references company(id),
  input_tokens bigint not null default 0, output_tokens bigint not null default 0,
  cache_read_tokens bigint not null default 0, cache_write_tokens bigint not null default 0,
  total_tokens bigint not null default 0,
  cost_estimate numeric(12,6),
  recorded_at timestamptz not null default now()
);

create table ai_request_artifact (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  request_id uuid not null references ai_request(id) on delete cascade,
  artifact_type text not null check (artifact_type in ('question','dashboard','card')),
  artifact_id uuid not null,
  status text not null default 'draft' check (status in ('draft','accepted','discarded'))
);

create table ai_feedback (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references company(id),
  user_id uuid not null references user_account(id),
  ai_request_id uuid not null references ai_request(id),
  rating smallint not null check (rating in (-1,1)),
  comment text, corrected_sql text,
  use_for_training boolean not null default false,       -- feeds golden set / doc refinement
  created_at timestamptz not null default now()
);
```
Draft artifacts are normal `saved_question`/`dashboard` rows flagged by an `ai_request_artifact.status='draft'` and hidden from lists until `accepted`.

## 9. Operations
```sql
create table audit_log (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references company(id), user_id uuid,
  action text not null,                  -- auth.login, datasource.create, query.run, ai.request, member.role_change ...
  entity_type text, entity_id uuid,
  metadata jsonb not null default '{}',  -- never secrets, never bound parameter values
  ip inet, user_agent text,
  created_at timestamptz not null default now()
);
create index audit_company_time on audit_log (company_id, created_at desc);

create table company_quota (
  company_id uuid primary key references company(id),
  ai_requests_per_month int not null default 200,
  queries_per_minute int not null default 120,
  max_datasources int not null default 5,
  max_rows_cap int not null default 100000
);
create table company_usage_counter (
  company_id uuid references company(id), period date,   -- first day of month
  ai_requests int not null default 0, ai_tokens bigint not null default 0, queries bigint not null default 0,
  primary key (company_id, period)
);
create table embed_token (                -- phase 7
  id uuid primary key default gen_random_uuid(), company_id uuid not null references company(id),
  resource_type text not null check (resource_type in ('dashboard','question')), resource_id uuid not null,
  token_hash text not null unique, locked_params jsonb not null default '{}',
  expires_at timestamptz, revoked_at timestamptz, created_by uuid, created_at timestamptz not null default now()
);
create table dashboard_subscription ( -- phase 7
  id uuid primary key default gen_random_uuid(), company_id uuid not null references company(id),
  dashboard_id uuid not null references dashboard(id), cron text not null, format text not null check (format in ('pdf','png','csv')),
  recipients text[] not null, params jsonb not null default '{}', enabled boolean not null default true
);
```

## 10. JSON column shapes
- `datasource.options`: `{"jdbc":{"loginTimeout":"10"},"pool":{"max":5,"idleSeconds":300},"ssh_tunnel":{"host":"","port":22,"user":"","key_secret_id":""}}` — `jdbc` keys must be in the dialect's allowlist (see `security.md`).
- `question_version.query_spec`, `parameters`: see `query-spec-and-parameters.md`.
- `question_version.viz_config`: validated by `chart_template.viz_schema` for `viz_template_key`.
- `dashboard_card.param_mappings`: `[{"dashboard_param":"date_range","question_param":"period","transform":null}]`.
- `ai_request.retrieval`: `{"docs":[{"doc_id":"…","table":"public.orders","score":0.83}],"glossary":["…"],"metrics":["…"],"templates":["line"]}`.
- `ai_request.validation`: `{"attempts":[{"n":1,"stage":"catalog","issues":[{"code":"UNKNOWN_COLUMN","detail":"o.total_amt"}]}]}`.
- `ai_request.result`: output contract of `prompts/query-generate.md` plus `question_draft_id`.

## 11. Retention and deletion
- `ai_run.prompt_text`: nulled after 30 days (configurable); `prompt_hash` kept.
- `query_run`: 90 days (aggregate afterwards). `audit_log`: 1 year.
- Company deletion: soft delete → 30-day grace → hard delete of all tenant rows, vector rows, Redis keys, **and provider agents** (`DELETE /v1/agents/{id}`).
- Datasource deletion: cascades catalog, docs, embeddings; questions referencing it are archived.

## 12. Index checklist
`(company_id, …)` composite on every list query; `doc_embedding` HNSW + `(company_id, datasource_id)`; `saved_question (company_id, collection_id)`; `dashboard_card (dashboard_id)`; `ai_run (agent_id, created_at)`; `ai_request (company_id, created_at desc)`; GIN on `question_version.parameters` only if searched.
