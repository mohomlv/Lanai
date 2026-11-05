create table user_account (
  id uniqueidentifier not null constraint user_account_pk primary key default newid(),
  email nvarchar(320) not null,
  password_hash nvarchar(max),
  display_name nvarchar(max) not null,
  email_verified bit not null constraint df_user_email_verified default 0,
  status nvarchar(20) not null constraint df_user_status default 'active'
    constraint user_account_status_check check (status in ('active','disabled')),
  last_login_at datetimeoffset,
  created_at datetimeoffset not null constraint df_user_created default sysdatetimeoffset(),
  updated_at datetimeoffset not null constraint df_user_updated default sysdatetimeoffset()
);
create unique index user_account_email_uq on user_account (email);

create table company (
  id uniqueidentifier not null constraint company_pk primary key default newid(),
  name nvarchar(max) not null,
  slug nvarchar(200) not null,
  owner_id uniqueidentifier not null constraint company_owner_fk references user_account(id),
  [plan] nvarchar(50) not null constraint df_company_plan default 'free',
  status nvarchar(20) not null constraint df_company_status default 'active'
    constraint company_status_check check (status in ('active','suspended','deleted')),
  settings nvarchar(max) not null constraint df_company_settings default '{}',
  created_at datetimeoffset not null constraint df_company_created default sysdatetimeoffset(),
  updated_at datetimeoffset not null constraint df_company_updated default sysdatetimeoffset(),
  deleted_at datetimeoffset
);
create unique index company_slug_uq on company (slug) where deleted_at is null;

create table membership (
  id uniqueidentifier not null constraint membership_pk primary key default newid(),
  company_id uniqueidentifier not null constraint membership_company_fk references company(id),
  user_id uniqueidentifier not null constraint membership_user_fk references user_account(id),
  role nvarchar(20) not null constraint membership_role_check check (role in ('owner','admin','editor','viewer')),
  status nvarchar(20) not null constraint df_membership_status default 'active'
    constraint membership_status_check check (status in ('active','suspended')),
  created_at datetimeoffset not null constraint df_membership_created default sysdatetimeoffset(),
  constraint membership_company_user_uq unique (company_id, user_id)
);
create index membership_user_idx on membership (user_id);

create table invitation (
  id uniqueidentifier not null constraint invitation_pk primary key default newid(),
  company_id uniqueidentifier not null constraint invitation_company_fk references company(id),
  email nvarchar(320) not null,
  role nvarchar(20) not null constraint invitation_role_check check (role in ('admin','editor','viewer')),
  token_hash nvarchar(128) not null constraint invitation_token_uq unique,
  invited_by uniqueidentifier not null constraint invitation_invited_fk references user_account(id),
  status nvarchar(20) not null constraint df_invitation_status default 'pending'
    constraint invitation_status_check check (status in ('pending','accepted','revoked','expired')),
  expires_at datetimeoffset not null,
  accepted_at datetimeoffset,
  created_at datetimeoffset not null constraint df_invitation_created default sysdatetimeoffset()
);

create table auth_token (
  id uniqueidentifier not null constraint auth_token_pk primary key default newid(),
  user_id uniqueidentifier not null constraint auth_token_user_fk references user_account(id),
  purpose nvarchar(30) not null constraint auth_token_purpose_check check (purpose in ('verify_email','reset_password')),
  token_hash nvarchar(128) not null constraint auth_token_hash_uq unique,
  expires_at datetimeoffset not null,
  used_at datetimeoffset,
  created_at datetimeoffset not null constraint df_auth_token_created default sysdatetimeoffset()
);

create table refresh_token (
  id uniqueidentifier not null constraint refresh_token_pk primary key default newid(),
  user_id uniqueidentifier not null constraint refresh_token_user_fk references user_account(id),
  company_id uniqueidentifier constraint refresh_token_company_fk references company(id),
  token_hash nvarchar(128) not null constraint refresh_token_hash_uq unique,
  family_id uniqueidentifier not null,
  expires_at datetimeoffset not null,
  revoked_at datetimeoffset,
  created_at datetimeoffset not null constraint df_refresh_token_created default sysdatetimeoffset()
);
create index refresh_token_family_idx on refresh_token (family_id);

create table api_key (
  id uniqueidentifier not null constraint api_key_pk primary key default newid(),
  company_id uniqueidentifier not null constraint api_key_company_fk references company(id),
  name nvarchar(max) not null,
  key_prefix nvarchar(64) not null,
  key_hash nvarchar(128) not null constraint api_key_hash_uq unique,
  scopes nvarchar(max) not null constraint df_api_key_scopes default '[]',
  created_by uniqueidentifier not null constraint api_key_created_fk references user_account(id),
  last_used_at datetimeoffset,
  expires_at datetimeoffset,
  revoked_at datetimeoffset,
  created_at datetimeoffset not null constraint df_api_key_created default sysdatetimeoffset()
);

create table datasource (
  id uniqueidentifier not null constraint datasource_pk primary key default newid(),
  company_id uniqueidentifier not null constraint datasource_company_fk references company(id),
  name nvarchar(200) not null,
  type nvarchar(30) not null constraint datasource_type_check check (type in (
    'postgres','mysql','mariadb','sqlserver','oracle','redshift','clickhouse','snowflake',
    'trino','starburst','presto','h2','sqlite'
  )),
  host nvarchar(max) not null,
  port int not null,
  database_name nvarchar(max) not null,
  username nvarchar(max) not null,
  secret_ciphertext varbinary(max) not null,
  secret_key_id nvarchar(max) not null,
  ssl_mode nvarchar(30) not null constraint df_datasource_ssl default 'require',
  options nvarchar(max) not null constraint df_datasource_options default '{}',
  description nvarchar(max),
  ai_sharing_level nvarchar(30) not null constraint df_datasource_share default 'schema_only'
    constraint datasource_share_check check (ai_sharing_level in ('none','schema_only','schema_and_samples')),
  query_timeout_seconds int not null constraint df_datasource_timeout default 30,
  max_rows int not null constraint df_datasource_rows default 10000,
  status nvarchar(20) not null constraint df_datasource_status default 'new'
    constraint datasource_status_check check (status in ('new','connected','error','disabled')),
  last_test_at datetimeoffset,
  last_test_error nvarchar(max),
  last_synced_at datetimeoffset,
  schema_fingerprint nvarchar(max),
  server_version nvarchar(max),
  created_by uniqueidentifier constraint datasource_created_fk references user_account(id),
  created_at datetimeoffset not null constraint df_datasource_created default sysdatetimeoffset(),
  updated_at datetimeoffset not null constraint df_datasource_updated default sysdatetimeoffset(),
  deleted_at datetimeoffset
);
create unique index datasource_name_uq on datasource (company_id, name) where deleted_at is null;

create table datasource_permission (
  id uniqueidentifier not null constraint datasource_permission_pk primary key default newid(),
  company_id uniqueidentifier not null constraint datasource_permission_company_fk references company(id),
  datasource_id uniqueidentifier not null constraint datasource_permission_ds_fk references datasource(id),
  role nvarchar(20) not null constraint datasource_permission_role_check check (role in ('admin','editor','viewer')),
  can_query bit not null constraint df_ds_perm_query default 1,
  can_native_sql bit not null constraint df_ds_perm_native default 0,
  can_manage bit not null constraint df_ds_perm_manage default 0,
  constraint datasource_permission_uq unique (datasource_id, role)
);

create table catalog_table (
  id uniqueidentifier not null constraint catalog_table_pk primary key default newid(),
  company_id uniqueidentifier not null constraint catalog_table_company_fk references company(id),
  datasource_id uniqueidentifier not null constraint catalog_table_ds_fk references datasource(id),
  schema_name nvarchar(256) not null,
  table_name nvarchar(256) not null,
  table_type nvarchar(30) not null constraint df_catalog_table_type default 'table'
    constraint catalog_table_type_check check (table_type in ('table','view','materialized_view')),
  row_estimate bigint,
  display_name nvarchar(max),
  user_note nvarchar(max),
  visible bit not null constraint df_catalog_table_visible default 1,
  removed_at datetimeoffset,
  created_at datetimeoffset not null constraint df_catalog_table_created default sysdatetimeoffset(),
  updated_at datetimeoffset not null constraint df_catalog_table_updated default sysdatetimeoffset(),
  constraint catalog_table_uq unique (datasource_id, schema_name, table_name)
);

create table catalog_column (
  id uniqueidentifier not null constraint catalog_column_pk primary key default newid(),
  company_id uniqueidentifier not null constraint catalog_column_company_fk references company(id),
  table_id uniqueidentifier not null constraint catalog_column_table_fk references catalog_table(id) on delete cascade,
  name nvarchar(256) not null,
  ordinal int not null,
  data_type nvarchar(max) not null,
  normalized_type nvarchar(20) not null constraint catalog_column_norm_check
    check (normalized_type in ('text','integer','decimal','boolean','date','datetime','time','json','binary','other')),
  semantic_type nvarchar(max),
  is_nullable bit not null constraint df_catalog_column_null default 1,
  is_pk bit not null constraint df_catalog_column_pk default 0,
  is_pii bit not null constraint df_catalog_column_pii default 0,
  exclude_from_samples bit not null constraint df_catalog_column_ex default 0,
  visible bit not null constraint df_catalog_column_vis default 1,
  display_name nvarchar(max),
  user_note nvarchar(max),
  sample_values nvarchar(max),
  distinct_count bigint,
  null_fraction numeric(5,4),
  constraint catalog_column_uq unique (table_id, name)
);

create table catalog_fk (
  id uniqueidentifier not null constraint catalog_fk_pk primary key default newid(),
  company_id uniqueidentifier not null constraint catalog_fk_company_fk references company(id),
  datasource_id uniqueidentifier not null constraint catalog_fk_ds_fk references datasource(id),
  constraint_name nvarchar(256) not null,
  ordinal int not null constraint df_catalog_fk_ord default 1,
  from_column_id uniqueidentifier not null constraint catalog_fk_from_fk references catalog_column(id) on delete cascade,
  to_column_id uniqueidentifier not null,
  is_inferred bit not null constraint df_catalog_fk_inf default 0,
  constraint catalog_fk_uq unique (datasource_id, constraint_name, ordinal)
);

create table schema_sync_schedule (
  datasource_id uniqueidentifier not null constraint schema_sync_pk primary key references datasource(id),
  company_id uniqueidentifier not null constraint schema_sync_company_fk references company(id),
  cron nvarchar(64) not null constraint df_schema_sync_cron default '0 3 * * *',
  enabled bit not null constraint df_schema_sync_en default 0,
  last_run_at datetimeoffset,
  next_run_at datetimeoffset
);

create table scrape_job (
  id uniqueidentifier not null constraint scrape_job_pk primary key default newid(),
  company_id uniqueidentifier not null constraint scrape_job_company_fk references company(id),
  datasource_id uniqueidentifier not null constraint scrape_job_ds_fk references datasource(id),
  status nvarchar(20) not null constraint df_scrape_job_status default 'queued'
    constraint scrape_job_status_check check (status in ('queued','running','done','partial','failed','cancelled')),
  [trigger] nvarchar(30) not null constraint scrape_job_trigger_check
    check ([trigger] in ('manual','schedule','schema_change','first_connect')),
  scope nvarchar(max),
  total_items int not null constraint df_scrape_job_total default 0,
  done_items int not null constraint df_scrape_job_done default 0,
  failed_items int not null constraint df_scrape_job_fail default 0,
  requested_by uniqueidentifier constraint scrape_job_req_fk references user_account(id),
  error nvarchar(max),
  started_at datetimeoffset,
  finished_at datetimeoffset,
  created_at datetimeoffset not null constraint df_scrape_job_created default sysdatetimeoffset()
);

create table scrape_job_item (
  id uniqueidentifier not null constraint scrape_job_item_pk primary key default newid(),
  company_id uniqueidentifier not null constraint scrape_job_item_company_fk references company(id),
  job_id uniqueidentifier not null constraint scrape_job_item_job_fk references scrape_job(id) on delete cascade,
  kind nvarchar(20) not null constraint scrape_job_item_kind_check check (kind in ('table','overview')),
  table_id uniqueidentifier constraint scrape_job_item_table_fk references catalog_table(id),
  status nvarchar(20) not null constraint df_scrape_item_status default 'queued'
    constraint scrape_job_item_status_check check (status in ('queued','running','done','failed','skipped')),
  attempts int not null constraint df_scrape_item_attempts default 0,
  error nvarchar(max),
  updated_at datetimeoffset not null constraint df_scrape_item_updated default sysdatetimeoffset()
);

create table schema_doc (
  id uniqueidentifier not null constraint schema_doc_pk primary key default newid(),
  company_id uniqueidentifier not null constraint schema_doc_company_fk references company(id),
  datasource_id uniqueidentifier not null constraint schema_doc_ds_fk references datasource(id),
  table_id uniqueidentifier constraint schema_doc_table_fk references catalog_table(id),
  kind nvarchar(30) not null constraint schema_doc_kind_check check (kind in ('table','database_overview')),
  markdown nvarchar(max) not null,
  content_hash nvarchar(128) not null,
  version int not null,
  source nvarchar(10) not null constraint schema_doc_source_check check (source in ('ai','user')),
  is_current bit not null constraint df_schema_doc_current default 1,
  review_status nvarchar(20) not null constraint df_schema_doc_review default 'accepted'
    constraint schema_doc_review_check check (review_status in ('accepted','proposed','rejected','superseded')),
  ai_run_id uniqueidentifier,
  created_by uniqueidentifier constraint schema_doc_created_fk references user_account(id),
  created_at datetimeoffset not null constraint df_schema_doc_created default sysdatetimeoffset(),
  table_key as isnull(table_id, '00000000-0000-0000-0000-000000000000') persisted
);
create unique index schema_doc_current_uq on schema_doc (datasource_id, table_key, kind) where is_current = 1;

create table doc_embedding (
  id uniqueidentifier not null constraint doc_embedding_pk primary key default newid(),
  company_id uniqueidentifier not null constraint doc_embedding_company_fk references company(id),
  datasource_id uniqueidentifier not null constraint doc_embedding_ds_fk references datasource(id),
  doc_id uniqueidentifier not null constraint doc_embedding_doc_fk references schema_doc(id) on delete cascade,
  chunk_index int not null,
  chunk_kind nvarchar(30) not null constraint doc_embedding_kind_check
    check (chunk_kind in ('header','columns','relationships','examples','overview')),
  chunk_text nvarchar(max) not null,
  token_count int,
  table_name nvarchar(256),
  embedding vector(1024) not null,
  created_at datetimeoffset not null constraint df_doc_embedding_created default sysdatetimeoffset()
);
create index doc_embedding_scope on doc_embedding (company_id, datasource_id);

create table glossary_term (
  id uniqueidentifier not null constraint glossary_term_pk primary key default newid(),
  company_id uniqueidentifier not null constraint glossary_term_company_fk references company(id),
  datasource_id uniqueidentifier constraint glossary_term_ds_fk references datasource(id),
  term nvarchar(max) not null,
  definition nvarchar(max) not null,
  synonyms nvarchar(max) not null constraint df_glossary_syn default '[]',
  created_by uniqueidentifier constraint glossary_term_created_fk references user_account(id),
  created_at datetimeoffset not null constraint df_glossary_created default sysdatetimeoffset()
);

create table metric_definition (
  id uniqueidentifier not null constraint metric_definition_pk primary key default newid(),
  company_id uniqueidentifier not null constraint metric_definition_company_fk references company(id),
  datasource_id uniqueidentifier not null constraint metric_definition_ds_fk references datasource(id),
  name nvarchar(max) not null,
  description nvarchar(max),
  expression_sql nvarchar(max) not null,
  base_table_id uniqueidentifier constraint metric_definition_table_fk references catalog_table(id),
  format nvarchar(max),
  created_by uniqueidentifier constraint metric_definition_created_fk references user_account(id),
  created_at datetimeoffset not null constraint df_metric_created default sysdatetimeoffset()
);

create table chart_template (
  id uniqueidentifier not null constraint chart_template_pk primary key default newid(),
  company_id uniqueidentifier constraint chart_template_company_fk references company(id),
  [key] nvarchar(100) not null,
  name nvarchar(max) not null,
  version int not null constraint df_chart_template_ver default 1,
  description nvarchar(max),
  markdown nvarchar(max) not null,
  viz_schema nvarchar(max) not null,
  data_requirements nvarchar(max) not null,
  example_viz_config nvarchar(max),
  example_sql nvarchar(max),
  source nvarchar(20) not null constraint df_chart_template_src default 'file'
    constraint chart_template_source_check check (source in ('file','custom')),
  content_hash nvarchar(128) not null,
  is_active bit not null constraint df_chart_template_active default 1,
  created_at datetimeoffset not null constraint df_chart_template_created default sysdatetimeoffset(),
  updated_at datetimeoffset not null constraint df_chart_template_updated default sysdatetimeoffset(),
  company_key as isnull(company_id, '00000000-0000-0000-0000-000000000000') persisted
);
create unique index chart_template_uq on chart_template (company_key, [key], version);

create table collection (
  id uniqueidentifier not null constraint collection_pk primary key default newid(),
  company_id uniqueidentifier not null constraint collection_company_fk references company(id),
  parent_id uniqueidentifier constraint collection_parent_fk references collection(id),
  name nvarchar(max) not null,
  description nvarchar(max),
  created_by uniqueidentifier constraint collection_created_fk references user_account(id),
  created_at datetimeoffset not null constraint df_collection_created default sysdatetimeoffset(),
  archived_at datetimeoffset
);

create table saved_question (
  id uniqueidentifier not null constraint saved_question_pk primary key default newid(),
  company_id uniqueidentifier not null constraint saved_question_company_fk references company(id),
  datasource_id uniqueidentifier not null constraint saved_question_ds_fk references datasource(id),
  collection_id uniqueidentifier constraint saved_question_col_fk references collection(id),
  name nvarchar(max) not null,
  description nvarchar(max),
  current_version_id uniqueidentifier,
  created_by uniqueidentifier constraint saved_question_created_fk references user_account(id),
  created_at datetimeoffset not null constraint df_saved_question_created default sysdatetimeoffset(),
  updated_at datetimeoffset not null constraint df_saved_question_updated default sysdatetimeoffset(),
  archived_at datetimeoffset
);
create index saved_question_collection_idx on saved_question (company_id, collection_id);

create table question_version (
  id uniqueidentifier not null constraint question_version_pk primary key default newid(),
  company_id uniqueidentifier not null constraint question_version_company_fk references company(id),
  question_id uniqueidentifier not null constraint question_version_q_fk references saved_question(id),
  version_no int not null,
  mode nvarchar(10) not null constraint question_version_mode_check check (mode in ('visual','native')),
  query_spec nvarchar(max),
  native_sql nvarchar(max),
  parameters nvarchar(max) not null constraint df_qv_params default '[]',
  viz_template_key nvarchar(64) not null constraint df_qv_viz default 'table',
  viz_config nvarchar(max) not null constraint df_qv_vizcfg default '{}',
  result_metadata nvarchar(max),
  change_note nvarchar(max),
  source nvarchar(10) not null constraint df_qv_source default 'user'
    constraint question_version_source_check check (source in ('user','ai')),
  ai_request_id uniqueidentifier,
  created_by uniqueidentifier constraint question_version_created_fk references user_account(id),
  created_at datetimeoffset not null constraint df_qv_created default sysdatetimeoffset(),
  constraint question_version_uq unique (question_id, version_no),
  constraint question_version_mode_body_check check (
    (mode = 'visual' and query_spec is not null) or (mode = 'native' and native_sql is not null)
  )
);
alter table saved_question add constraint saved_question_current_version_fk
  foreign key (current_version_id) references question_version(id);

create table parameter_value_cache (
  id uniqueidentifier not null constraint parameter_value_cache_pk primary key default newid(),
  company_id uniqueidentifier not null constraint pvc_company_fk references company(id),
  datasource_id uniqueidentifier not null constraint pvc_ds_fk references datasource(id),
  source_hash nvarchar(128) not null,
  [values] nvarchar(max) not null,
  refreshed_at datetimeoffset not null constraint df_pvc_refreshed default sysdatetimeoffset(),
  constraint pvc_uq unique (datasource_id, source_hash)
);

create table dashboard (
  id uniqueidentifier not null constraint dashboard_pk primary key default newid(),
  company_id uniqueidentifier not null constraint dashboard_company_fk references company(id),
  collection_id uniqueidentifier constraint dashboard_col_fk references collection(id),
  name nvarchar(max) not null,
  description nvarchar(max),
  auto_refresh_seconds int,
  settings nvarchar(max) not null constraint df_dashboard_settings default '{}',
  created_by uniqueidentifier constraint dashboard_created_fk references user_account(id),
  created_at datetimeoffset not null constraint df_dashboard_created default sysdatetimeoffset(),
  updated_at datetimeoffset not null constraint df_dashboard_updated default sysdatetimeoffset(),
  archived_at datetimeoffset
);

create table dashboard_tab (
  id uniqueidentifier not null constraint dashboard_tab_pk primary key default newid(),
  company_id uniqueidentifier not null constraint dashboard_tab_company_fk references company(id),
  dashboard_id uniqueidentifier not null constraint dashboard_tab_dash_fk references dashboard(id) on delete cascade,
  name nvarchar(max) not null,
  position int not null constraint df_dashboard_tab_pos default 0
);

create table dashboard_card (
  id uniqueidentifier not null constraint dashboard_card_pk primary key default newid(),
  company_id uniqueidentifier not null constraint dashboard_card_company_fk references company(id),
  dashboard_id uniqueidentifier not null constraint dashboard_card_dash_fk references dashboard(id) on delete cascade,
  tab_id uniqueidentifier constraint dashboard_card_tab_fk references dashboard_tab(id),
  card_type nvarchar(20) not null constraint dashboard_card_type_check check (card_type in ('question','text','heading')),
  question_id uniqueidentifier constraint dashboard_card_q_fk references saved_question(id),
  text_content nvarchar(max),
  x int not null,
  y int not null,
  w int not null,
  h int not null,
  viz_overrides nvarchar(max) not null constraint df_dc_viz default '{}',
  param_mappings nvarchar(max) not null constraint df_dc_params default '[]',
  constraint dashboard_card_body_check check ((card_type = 'question' and question_id is not null) or card_type <> 'question')
);
create index dashboard_card_dashboard_idx on dashboard_card (dashboard_id);

create table dashboard_parameter (
  id uniqueidentifier not null constraint dashboard_parameter_pk primary key default newid(),
  company_id uniqueidentifier not null constraint dashboard_parameter_company_fk references company(id),
  dashboard_id uniqueidentifier not null constraint dashboard_parameter_dash_fk references dashboard(id) on delete cascade,
  slug nvarchar(100) not null,
  label nvarchar(max) not null,
  type nvarchar(20) not null constraint dashboard_parameter_type_check
    check (type in ('text','number','date','date_range','select','select_multi','boolean')),
  default_value nvarchar(max),
  required bit not null constraint df_dp_req default 0,
  options_source nvarchar(max),
  position int not null constraint df_dp_pos default 0,
  constraint dashboard_parameter_uq unique (dashboard_id, slug)
);

create table query_run (
  id uniqueidentifier not null constraint query_run_pk primary key default newid(),
  company_id uniqueidentifier not null constraint query_run_company_fk references company(id),
  user_id uniqueidentifier constraint query_run_user_fk references user_account(id),
  datasource_id uniqueidentifier not null constraint query_run_ds_fk references datasource(id),
  question_id uniqueidentifier,
  dashboard_id uniqueidentifier,
  ai_request_id uniqueidentifier,
  source nvarchar(20) not null constraint query_run_source_check
    check (source in ('editor','card','dashboard','ai_validation','api','embed')),
  sql_hash nvarchar(128) not null,
  params_hash nvarchar(128),
  compiled_sql nvarchar(max),
  status nvarchar(20) not null constraint query_run_status_check
    check (status in ('success','error','timeout','cancelled','rejected')),
  row_count int,
  duration_ms int,
  cache_hit bit not null constraint df_query_run_cache default 0,
  error_code nvarchar(64),
  error_message nvarchar(max),
  started_at datetimeoffset not null constraint df_query_run_started default sysdatetimeoffset()
);
create index query_run_company_time on query_run (company_id, started_at desc);

create table ai_provider_config (
  id uniqueidentifier not null constraint ai_provider_config_pk primary key default newid(),
  company_id uniqueidentifier constraint ai_provider_config_company_fk references company(id),
  provider nvarchar(40) not null,
  api_key_ciphertext varbinary(max) not null,
  api_key_key_id nvarchar(max) not null,
  default_model_id nvarchar(max),
  model_params nvarchar(max) not null constraint df_aipc_params default '[]',
  settings nvarchar(max) not null constraint df_aipc_settings default '{}',
  is_active bit not null constraint df_aipc_active default 1,
  created_at datetimeoffset not null constraint df_aipc_created default sysdatetimeoffset(),
  updated_at datetimeoffset not null constraint df_aipc_updated default sysdatetimeoffset()
);

create table ai_conversation (
  id uniqueidentifier not null constraint ai_conversation_pk primary key default newid(),
  company_id uniqueidentifier not null constraint ai_conversation_company_fk references company(id),
  user_id uniqueidentifier not null constraint ai_conversation_user_fk references user_account(id),
  datasource_id uniqueidentifier constraint ai_conversation_ds_fk references datasource(id),
  provider_config_id uniqueidentifier constraint ai_conversation_provider_fk references ai_provider_config(id),
  target_type nvarchar(20) not null constraint ai_conversation_target_check check (target_type in ('question','dashboard')),
  title nvarchar(max),
  status nvarchar(20) not null constraint df_ai_conv_status default 'open'
    constraint ai_conversation_status_check check (status in ('open','closed')),
  created_at datetimeoffset not null constraint df_ai_conv_created default sysdatetimeoffset(),
  updated_at datetimeoffset not null constraint df_ai_conv_updated default sysdatetimeoffset()
);

create table ai_message (
  id uniqueidentifier not null constraint ai_message_pk primary key default newid(),
  company_id uniqueidentifier not null constraint ai_message_company_fk references company(id),
  conversation_id uniqueidentifier not null constraint ai_message_conv_fk references ai_conversation(id) on delete cascade,
  role nvarchar(20) not null constraint ai_message_role_check check (role in ('user','assistant','system')),
  content nvarchar(max) not null,
  generated nvarchar(max),
  validation_status nvarchar(30) constraint ai_message_val_check
    check (validation_status in ('valid','repaired','invalid','clarification','n/a')),
  created_at datetimeoffset not null constraint df_ai_message_created default sysdatetimeoffset()
);

create table ai_agent (
  id uniqueidentifier not null constraint ai_agent_pk primary key,
  company_id uniqueidentifier not null constraint ai_agent_company_fk references company(id),
  provider nvarchar(40) not null,
  provider_agent_id nvarchar(200) not null constraint ai_agent_provider_uq unique,
  purpose nvarchar(40) not null constraint ai_agent_purpose_check
    check (purpose in ('scrape','query_assistant','dashboard_planner','doc_refiner')),
  conversation_id uniqueidentifier constraint ai_agent_conv_fk references ai_conversation(id),
  scrape_job_id uniqueidentifier constraint ai_agent_job_fk references scrape_job(id),
  status nvarchar(20) not null constraint df_ai_agent_status default 'creating'
    constraint ai_agent_status_check check (status in ('creating','active','idle','archived','deleted','failed')),
  model_id nvarchar(max),
  created_at datetimeoffset not null constraint df_ai_agent_created default sysdatetimeoffset(),
  updated_at datetimeoffset not null constraint df_ai_agent_updated default sysdatetimeoffset()
);

create table ai_request (
  id uniqueidentifier not null constraint ai_request_pk primary key default newid(),
  company_id uniqueidentifier not null constraint ai_request_company_fk references company(id),
  user_id uniqueidentifier not null constraint ai_request_user_fk references user_account(id),
  conversation_id uniqueidentifier constraint ai_request_conv_fk references ai_conversation(id),
  datasource_id uniqueidentifier constraint ai_request_ds_fk references datasource(id),
  provider_config_id uniqueidentifier constraint ai_request_provider_fk references ai_provider_config(id),
  target_type nvarchar(20) not null constraint ai_request_target_check check (target_type in ('question','dashboard')),
  status nvarchar(30) not null constraint df_ai_request_status default 'queued'
    constraint ai_request_status_check check (status in
    ('queued','retrieving','generating','validating','repairing','ready','needs_clarification','failed','accepted','discarded')),
  user_prompt nvarchar(max) not null,
  retrieval nvarchar(max),
  [plan] nvarchar(max),
  result nvarchar(max),
  validation nvarchar(max),
  retry_count int not null constraint df_ai_request_retry default 0,
  error_code nvarchar(64),
  error_message nvarchar(max),
  idempotency_key nvarchar(128),
  created_at datetimeoffset not null constraint df_ai_request_created default sysdatetimeoffset(),
  finished_at datetimeoffset
);
create unique index ai_request_idem_uq on ai_request (company_id, idempotency_key) where idempotency_key is not null;
create index ai_request_company_time on ai_request (company_id, created_at desc);

create table ai_run (
  id uniqueidentifier not null constraint ai_run_pk primary key default newid(),
  company_id uniqueidentifier not null constraint ai_run_company_fk references company(id),
  agent_id uniqueidentifier not null constraint ai_run_agent_fk references ai_agent(id),
  provider_run_id nvarchar(max),
  kind nvarchar(30) not null constraint ai_run_kind_check
    check (kind in ('scrape_table','scrape_overview','generate_query','repair_query','plan_dashboard','refine_doc')),
  ai_request_id uniqueidentifier constraint ai_run_req_fk references ai_request(id),
  scrape_job_item_id uniqueidentifier constraint ai_run_item_fk references scrape_job_item(id),
  prompt_template nvarchar(max) not null,
  prompt_version nvarchar(max) not null,
  prompt_text nvarchar(max),
  prompt_hash nvarchar(128) not null,
  status nvarchar(20) not null constraint df_ai_run_status default 'creating'
    constraint ai_run_status_check check (status in ('creating','running','finished','error','cancelled','expired','timeout')),
  result_text nvarchar(max),
  parsed_result nvarchar(max),
  error_code nvarchar(64),
  error_message nvarchar(max),
  duration_ms int,
  started_at datetimeoffset,
  finished_at datetimeoffset,
  created_at datetimeoffset not null constraint df_ai_run_created default sysdatetimeoffset()
);
create index ai_run_agent_idx on ai_run (agent_id, created_at);
alter table schema_doc add constraint schema_doc_ai_run_fk foreign key (ai_run_id) references ai_run(id);

create table ai_usage (
  run_id uniqueidentifier not null constraint ai_usage_pk primary key references ai_run(id) on delete cascade,
  company_id uniqueidentifier not null constraint ai_usage_company_fk references company(id),
  input_tokens bigint not null constraint df_ai_usage_in default 0,
  output_tokens bigint not null constraint df_ai_usage_out default 0,
  cache_read_tokens bigint not null constraint df_ai_usage_cr default 0,
  cache_write_tokens bigint not null constraint df_ai_usage_cw default 0,
  total_tokens bigint not null constraint df_ai_usage_tot default 0,
  cost_estimate numeric(12,6),
  recorded_at datetimeoffset not null constraint df_ai_usage_rec default sysdatetimeoffset()
);

create table ai_request_artifact (
  id uniqueidentifier not null constraint ai_request_artifact_pk primary key default newid(),
  company_id uniqueidentifier not null constraint ai_request_artifact_company_fk references company(id),
  request_id uniqueidentifier not null constraint ai_request_artifact_req_fk references ai_request(id) on delete cascade,
  artifact_type nvarchar(20) not null constraint ai_request_artifact_type_check check (artifact_type in ('question','dashboard','card')),
  artifact_id uniqueidentifier not null,
  status nvarchar(20) not null constraint df_aira_status default 'draft'
    constraint ai_request_artifact_status_check check (status in ('draft','accepted','discarded'))
);

create table ai_feedback (
  id uniqueidentifier not null constraint ai_feedback_pk primary key default newid(),
  company_id uniqueidentifier not null constraint ai_feedback_company_fk references company(id),
  user_id uniqueidentifier not null constraint ai_feedback_user_fk references user_account(id),
  ai_request_id uniqueidentifier not null constraint ai_feedback_req_fk references ai_request(id),
  rating smallint not null constraint ai_feedback_rating_check check (rating in (-1, 1)),
  comment nvarchar(max),
  corrected_sql nvarchar(max),
  use_for_training bit not null constraint df_ai_feedback_train default 0,
  created_at datetimeoffset not null constraint df_ai_feedback_created default sysdatetimeoffset()
);

create table audit_log (
  id uniqueidentifier not null constraint audit_log_pk primary key default newid(),
  company_id uniqueidentifier constraint audit_log_company_fk references company(id),
  user_id uniqueidentifier,
  action nvarchar(max) not null,
  entity_type nvarchar(max),
  entity_id uniqueidentifier,
  metadata nvarchar(max) not null constraint df_audit_meta default '{}',
  ip nvarchar(64),
  user_agent nvarchar(max),
  created_at datetimeoffset not null constraint df_audit_created default sysdatetimeoffset()
);
create index audit_company_time on audit_log (company_id, created_at desc);

create table company_quota (
  company_id uniqueidentifier not null constraint company_quota_pk primary key references company(id),
  ai_requests_per_month int not null constraint df_quota_ai default 200,
  queries_per_minute int not null constraint df_quota_qpm default 120,
  max_datasources int not null constraint df_quota_ds default 5,
  max_rows_cap int not null constraint df_quota_rows default 100000
);

create table company_usage_counter (
  company_id uniqueidentifier not null constraint company_usage_company_fk references company(id),
  period date not null,
  ai_requests int not null constraint df_usage_ai default 0,
  ai_tokens bigint not null constraint df_usage_tok default 0,
  queries bigint not null constraint df_usage_q default 0,
  constraint company_usage_counter_pk primary key (company_id, period)
);

create table embed_token (
  id uniqueidentifier not null constraint embed_token_pk primary key default newid(),
  company_id uniqueidentifier not null constraint embed_token_company_fk references company(id),
  resource_type nvarchar(20) not null constraint embed_token_type_check check (resource_type in ('dashboard','question')),
  resource_id uniqueidentifier not null,
  token_hash nvarchar(128) not null constraint embed_token_hash_uq unique,
  locked_params nvarchar(max) not null constraint df_embed_params default '{}',
  expires_at datetimeoffset,
  revoked_at datetimeoffset,
  created_by uniqueidentifier,
  created_at datetimeoffset not null constraint df_embed_created default sysdatetimeoffset()
);
