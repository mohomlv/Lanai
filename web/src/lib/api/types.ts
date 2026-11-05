export type Page<T> = { items: T[]; page?: number; total?: number };

export type MembershipView = {
  company_id: string;
  company_name?: string;
  role: string;
  branch_ids?: number[];
};

export type Me = {
  id: string;
  email: string;
  username?: string;
  display_name: string;
  email_verified?: boolean;
  company_id?: string;
  role?: string;
  platform_admin?: boolean;
  branch_ids?: number[];
  memberships?: MembershipView[];
};

export type PlatformUser = {
  user_id: string;
  email?: string;
  username?: string;
  display_name?: string;
  status?: string;
  platform_admin?: boolean;
  memberships?: MembershipView[];
};

export type CompanyMember = {
  user_id: string;
  email?: string;
  username?: string;
  display_name?: string;
  role?: string;
  branch_ids?: number[];
};

export type AuthTokens = {
  access_token: string;
  refresh_token: string;
  user_id: string;
  company_id?: string;
  role?: string;
};

export type Datasource = {
  id: string;
  name: string;
  type: string;
  host: string;
  port: number;
  database_name: string;
  username: string;
  has_password?: boolean;
  ssl_mode?: string;
  description?: string;
  status?: string;
  ai_sharing_level?: string;
  last_synced_at?: string;
  last_test_error?: string;
};

export type CatalogColumn = {
  id: string;
  name: string;
  native_type?: string;
  normalized_type?: string;
  nullable?: boolean;
  is_pk?: boolean;
  is_pii?: boolean;
  visible?: boolean;
  display_name?: string;
  user_note?: string;
};

export type CatalogTable = {
  id: string;
  schema_name?: string;
  table_name: string;
  table_type?: string;
  visible?: boolean;
  display_name?: string;
  user_note?: string;
  columns?: CatalogColumn[];
};

export type SchemaDoc = {
  id: string;
  kind: string;
  title?: string;
  table_id?: string;
  markdown?: string;
  version?: number;
  source?: string;
  is_current?: boolean;
};

export type AiRequest = {
  id: string;
  status: string;
  error_code?: string;
  error_message?: string;
  result?: {
    native_sql?: string;
    summary?: string;
    parameters?: unknown[];
    viz?: { template?: string; config?: unknown };
    question_id?: string;
    name?: string;
  };
};

export type QueryResult = {
  run_id?: string;
  columns: { name: string; type?: string; native_type?: string }[];
  rows: unknown[][];
  truncated?: boolean;
  duration_ms?: number;
  sql?: string;
};

export type Question = {
  id: string;
  name: string;
  description?: string;
  datasource_id: string;
  collection_id?: string;
  mode?: string;
  native_sql?: string;
  query_spec?: unknown;
  parameters?: unknown[];
  viz_template?: string;
  viz_config?: unknown;
  is_parameter?: boolean;
  parameter_fields?: {
    fields?: string[];
    value_field?: string;
    display_field?: string;
  };
};

export type Dashboard = {
  id: string;
  name: string;
  description?: string;
  tabs?: { id: string; name: string; cards?: DashboardCard[] }[];
  parameters?: unknown[];
};

export type DashboardCard = {
  id: string;
  card_type: string;
  question_id?: string;
  tab_id?: string;
  x?: number;
  y?: number;
  w?: number;
  h?: number;
  text_content?: string;
};
