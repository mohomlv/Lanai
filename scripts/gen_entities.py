"""One-shot generator for JPA entities. Run, then delete is optional; Java is the source."""
from pathlib import Path

ROOT = Path(r"d:\Smart-Dashboard\landata\src\main\java")

HEADER = """package {pkg};

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
{extra}

@Entity
@Table(name = "{table}")
public class {name} {extends} {{
"""

# (type, name, column, ann, init)
# ann is extra annotations before the field


def emit(pkg, name, table, fields, extend=None):
    extra = ""
    if extend:
        extra = f"import {extend.rsplit('.', 1)[0]}.*;\n" if False else ""
    extends = f"extends {extend} " if extend else ""
    # fix import of superclass if same... we'll use simple name and import
    body = []
    imports = set()
    if extend:
        imports.add(f"import com.landata.shared.domain.{extend};\n")
    lines = [HEADER.format(pkg=pkg, table=table, name=name, extends=extends, extra="".join(imports))]
    accessors = []
    for typ, fname, column, ann, init in fields:
        if ann:
            lines.append(ann + "\n")
        col = f'  @Column(name = "{column}")\n' if column else ""
        # id column annotation already in ann sometimes
        init_s = f" = {init}" if init else ""
        lines.append(f"{col}  private {typ} {fname}{init_s};\n\n")
        cap = fname[0].upper() + fname[1:]
        accessors.append(f"  public {typ} get{cap}() {{ return {fname}; }}\n")
        accessors.append(f"  public void set{cap}({typ} {fname}) {{ this.{fname} = {fname}; }}\n\n")
    lines.append("  @PrePersist\n  void prePersist() {\n")
    names = {f[1] for f in fields}
    if "id" in names:
        lines.append("    if (id == null) id = UUID.randomUUID();\n")
    if "createdAt" in names:
        lines.append("    if (createdAt == null) createdAt = Instant.now();\n")
    if "updatedAt" in names:
        lines.append("    if (updatedAt == null) updatedAt = Instant.now();\n")
    lines.append("  }\n\n")
    lines.extend(accessors)
    lines.append("}\n")
    path = ROOT.joinpath(*pkg.split("."), name + ".java")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(lines), encoding="utf-8")
    print(path)


JSON = '@JdbcTypeCode(SqlTypes.JSON)\n'
ARR = '@JdbcTypeCode(SqlTypes.ARRAY)\n'
ID = '@Id\n'

def F(typ, name, column=None, ann="", init=None):
    col = column if column else name
    # camel to snake if column is None and name has caps
    return (typ, name, col, ann, init)

entities = []

# identity
emit("com.landata.identity.domain", "UserAccount", "user_account", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("String", "email", "email"),
    F("String", "passwordHash", "password_hash"),
    F("String", "displayName", "display_name"),
    F("boolean", "emailVerified", "email_verified", "", "false"),
    F("String", "status", "status", "", '"active"'),
    F("Instant", "lastLoginAt", "last_login_at"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "updatedAt", "updated_at"),
])

emit("com.landata.identity.domain", "Company", "company", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("String", "name", "name"),
    F("String", "slug", "slug"),
    F("UUID", "ownerId", "owner_id"),
    F("String", "plan", "plan", "", '"free"'),
    F("String", "status", "status", "", '"active"'),
    F("Map<String, Object>", "settings", "settings", JSON, "new LinkedHashMap<>()"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "updatedAt", "updated_at"),
    F("Instant", "deletedAt", "deleted_at"),
])

emit("com.landata.identity.domain", "Membership", "membership", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "userId", "user_id"),
    F("String", "role", "role"),
    F("String", "status", "status", "", '"active"'),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.identity.domain", "Invitation", "invitation", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("String", "email", "email"),
    F("String", "role", "role"),
    F("String", "tokenHash", "token_hash"),
    F("UUID", "invitedBy", "invited_by"),
    F("String", "status", "status", "", '"pending"'),
    F("Instant", "expiresAt", "expires_at"),
    F("Instant", "acceptedAt", "accepted_at"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.identity.domain", "AuthToken", "auth_token", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "userId", "user_id"),
    F("String", "purpose", "purpose"),
    F("String", "tokenHash", "token_hash"),
    F("Instant", "expiresAt", "expires_at"),
    F("Instant", "usedAt", "used_at"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.identity.domain", "RefreshToken", "refresh_token", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "userId", "user_id"),
    F("String", "tokenHash", "token_hash"),
    F("UUID", "familyId", "family_id"),
    F("Instant", "expiresAt", "expires_at"),
    F("Instant", "revokedAt", "revoked_at"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.identity.domain", "ApiKey", "api_key", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("String", "name", "name"),
    F("String", "keyPrefix", "key_prefix"),
    F("String", "keyHash", "key_hash"),
    F("List<String>", "scopes", "scopes", ARR, "new ArrayList<>()"),
    F("UUID", "createdBy", "created_by"),
    F("Instant", "lastUsedAt", "last_used_at"),
    F("Instant", "expiresAt", "expires_at"),
    F("Instant", "revokedAt", "revoked_at"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.datasource.domain", "Datasource", "datasource", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("String", "name", "name"),
    F("String", "type", "type"),
    F("String", "host", "host"),
    F("int", "port", "port"),
    F("String", "databaseName", "database_name"),
    F("String", "username", "username"),
    F("byte[]", "secretCiphertext", "secret_ciphertext"),
    F("String", "secretKeyId", "secret_key_id"),
    F("String", "sslMode", "ssl_mode", "", '"require"'),
    F("Map<String, Object>", "options", "options", JSON, "new LinkedHashMap<>()"),
    F("String", "description", "description"),
    F("String", "aiSharingLevel", "ai_sharing_level", "", '"schema_only"'),
    F("int", "queryTimeoutSeconds", "query_timeout_seconds", "", "30"),
    F("int", "maxRows", "max_rows", "", "10000"),
    F("String", "status", "status", "", '"new"'),
    F("Instant", "lastTestAt", "last_test_at"),
    F("String", "lastTestError", "last_test_error"),
    F("Instant", "lastSyncedAt", "last_synced_at"),
    F("String", "schemaFingerprint", "schema_fingerprint"),
    F("String", "serverVersion", "server_version"),
    F("UUID", "createdBy", "created_by"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "updatedAt", "updated_at"),
    F("Instant", "deletedAt", "deleted_at"),
])

emit("com.landata.datasource.domain", "DatasourcePermission", "datasource_permission", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("String", "role", "role"),
    F("boolean", "canQuery", "can_query", "", "true"),
    F("boolean", "canNativeSql", "can_native_sql", "", "false"),
    F("boolean", "canManage", "can_manage", "", "false"),
])

emit("com.landata.datasource.domain", "CatalogTable", "catalog_table", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("String", "schemaName", "schema_name"),
    F("String", "tableName", "table_name"),
    F("String", "tableType", "table_type", "", '"table"'),
    F("Long", "rowEstimate", "row_estimate"),
    F("String", "displayName", "display_name"),
    F("String", "userNote", "user_note"),
    F("boolean", "visible", "visible", "", "true"),
    F("Instant", "removedAt", "removed_at"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "updatedAt", "updated_at"),
])

emit("com.landata.datasource.domain", "CatalogColumn", "catalog_column", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "tableId", "table_id"),
    F("String", "name", "name"),
    F("int", "ordinal", "ordinal"),
    F("String", "dataType", "data_type"),
    F("String", "normalizedType", "normalized_type"),
    F("String", "semanticType", "semantic_type"),
    F("boolean", "nullable", "is_nullable", "", "true"),
    F("boolean", "pk", "is_pk", "", "false"),
    F("boolean", "pii", "is_pii", "", "false"),
    F("boolean", "excludeFromSamples", "exclude_from_samples", "", "false"),
    F("boolean", "visible", "visible", "", "true"),
    F("String", "displayName", "display_name"),
    F("String", "userNote", "user_note"),
    F("List<Object>", "sampleValues", "sample_values", JSON),
    F("Long", "distinctCount", "distinct_count"),
    F("BigDecimal", "nullFraction", "null_fraction"),
])

emit("com.landata.datasource.domain", "CatalogFk", "catalog_fk", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("String", "constraintName", "constraint_name"),
    F("int", "ordinal", "ordinal", "", "1"),
    F("UUID", "fromColumnId", "from_column_id"),
    F("UUID", "toColumnId", "to_column_id"),
    F("boolean", "inferred", "is_inferred", "", "false"),
])

emit("com.landata.datasource.domain", "SchemaSyncSchedule", "schema_sync_schedule", [
    F("UUID", "datasourceId", "datasource_id", ID),
    F("UUID", "companyId", "company_id"),
    F("String", "cron", "cron", "", '"0 3 * * *"'),
    F("boolean", "enabled", "enabled", "", "false"),
    F("Instant", "lastRunAt", "last_run_at"),
    F("Instant", "nextRunAt", "next_run_at"),
])

emit("com.landata.knowledge.domain", "ScrapeJob", "scrape_job", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("String", "status", "status", "", '"queued"'),
    F("String", "trigger", "trigger"),
    F("Map<String, Object>", "scope", "scope", JSON),
    F("int", "totalItems", "total_items", "", "0"),
    F("int", "doneItems", "done_items", "", "0"),
    F("int", "failedItems", "failed_items", "", "0"),
    F("UUID", "requestedBy", "requested_by"),
    F("String", "error", "error"),
    F("Instant", "startedAt", "started_at"),
    F("Instant", "finishedAt", "finished_at"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.knowledge.domain", "ScrapeJobItem", "scrape_job_item", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "jobId", "job_id"),
    F("String", "kind", "kind"),
    F("UUID", "tableId", "table_id"),
    F("String", "status", "status", "", '"queued"'),
    F("int", "attempts", "attempts", "", "0"),
    F("String", "error", "error"),
    F("Instant", "updatedAt", "updated_at"),
])

emit("com.landata.knowledge.domain", "SchemaDoc", "schema_doc", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("UUID", "tableId", "table_id"),
    F("String", "kind", "kind"),
    F("String", "markdown", "markdown"),
    F("String", "contentHash", "content_hash"),
    F("int", "version", "version", "", "1"),
    F("String", "source", "source"),
    F("boolean", "current", "is_current", "", "true"),
    F("String", "reviewStatus", "review_status", "", '"accepted"'),
    F("UUID", "aiRunId", "ai_run_id"),
    F("UUID", "createdBy", "created_by"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.knowledge.domain", "GlossaryTerm", "glossary_term", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("String", "term", "term"),
    F("String", "definition", "definition"),
    F("List<String>", "synonyms", "synonyms", ARR, "new ArrayList<>()"),
    F("UUID", "createdBy", "created_by"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.knowledge.domain", "MetricDefinition", "metric_definition", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("String", "name", "name"),
    F("String", "description", "description"),
    F("String", "expressionSql", "expression_sql"),
    F("UUID", "baseTableId", "base_table_id"),
    F("String", "format", "format"),
    F("UUID", "createdBy", "created_by"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.viz.domain", "ChartTemplate", "chart_template", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("String", "key", "key"),
    F("String", "name", "name"),
    F("int", "version", "version", "", "1"),
    F("String", "description", "description"),
    F("String", "markdown", "markdown"),
    F("Map<String, Object>", "vizSchema", "viz_schema", JSON, "new LinkedHashMap<>()"),
    F("Map<String, Object>", "dataRequirements", "data_requirements", JSON, "new LinkedHashMap<>()"),
    F("Map<String, Object>", "exampleVizConfig", "example_viz_config", JSON),
    F("String", "exampleSql", "example_sql"),
    F("String", "source", "source", "", '"file"'),
    F("String", "contentHash", "content_hash"),
    F("boolean", "active", "is_active", "", "true"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "updatedAt", "updated_at"),
])

emit("com.landata.query.domain", "CollectionFolder", "collection", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "parentId", "parent_id"),
    F("String", "name", "name"),
    F("String", "description", "description"),
    F("UUID", "createdBy", "created_by"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "archivedAt", "archived_at"),
])

emit("com.landata.query.domain", "SavedQuestion", "saved_question", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("UUID", "collectionId", "collection_id"),
    F("String", "name", "name"),
    F("String", "description", "description"),
    F("UUID", "currentVersionId", "current_version_id"),
    F("UUID", "createdBy", "created_by"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "updatedAt", "updated_at"),
    F("Instant", "archivedAt", "archived_at"),
])

emit("com.landata.query.domain", "QuestionVersion", "question_version", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "questionId", "question_id"),
    F("int", "versionNo", "version_no"),
    F("String", "mode", "mode"),
    F("Map<String, Object>", "querySpec", "query_spec", JSON),
    F("String", "nativeSql", "native_sql"),
    F("List<Map<String, Object>>", "parameters", "parameters", JSON, "new ArrayList<>()"),
    F("String", "vizTemplateKey", "viz_template_key", "", '"table"'),
    F("Map<String, Object>", "vizConfig", "viz_config", JSON, "new LinkedHashMap<>()"),
    F("Map<String, Object>", "resultMetadata", "result_metadata", JSON),
    F("String", "changeNote", "change_note"),
    F("String", "source", "source", "", '"user"'),
    F("UUID", "aiRequestId", "ai_request_id"),
    F("UUID", "createdBy", "created_by"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.query.domain", "QueryRun", "query_run", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "userId", "user_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("UUID", "questionId", "question_id"),
    F("UUID", "dashboardId", "dashboard_id"),
    F("UUID", "aiRequestId", "ai_request_id"),
    F("String", "source", "source"),
    F("String", "sqlHash", "sql_hash"),
    F("String", "paramsHash", "params_hash"),
    F("String", "compiledSql", "compiled_sql"),
    F("String", "status", "status"),
    F("Integer", "rowCount", "row_count"),
    F("Integer", "durationMs", "duration_ms"),
    F("boolean", "cacheHit", "cache_hit", "", "false"),
    F("String", "errorCode", "error_code"),
    F("String", "errorMessage", "error_message"),
    F("Instant", "startedAt", "started_at"),
])

emit("com.landata.dashboard.domain", "Dashboard", "dashboard", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "collectionId", "collection_id"),
    F("String", "name", "name"),
    F("String", "description", "description"),
    F("Integer", "autoRefreshSeconds", "auto_refresh_seconds"),
    F("Map<String, Object>", "settings", "settings", JSON, "new LinkedHashMap<>()"),
    F("UUID", "createdBy", "created_by"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "updatedAt", "updated_at"),
    F("Instant", "archivedAt", "archived_at"),
])

emit("com.landata.dashboard.domain", "DashboardTab", "dashboard_tab", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "dashboardId", "dashboard_id"),
    F("String", "name", "name"),
    F("int", "position", "position", "", "0"),
])

emit("com.landata.dashboard.domain", "DashboardCard", "dashboard_card", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "dashboardId", "dashboard_id"),
    F("UUID", "tabId", "tab_id"),
    F("String", "cardType", "card_type"),
    F("UUID", "questionId", "question_id"),
    F("String", "textContent", "text_content"),
    F("int", "x", "x"),
    F("int", "y", "y"),
    F("int", "w", "w"),
    F("int", "h", "h"),
    F("Map<String, Object>", "vizOverrides", "viz_overrides", JSON, "new LinkedHashMap<>()"),
    F("List<Map<String, Object>>", "paramMappings", "param_mappings", JSON, "new ArrayList<>()"),
])

emit("com.landata.dashboard.domain", "DashboardParameter", "dashboard_parameter", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "dashboardId", "dashboard_id"),
    F("String", "slug", "slug"),
    F("String", "label", "label"),
    F("String", "type", "type"),
    F("Object", "defaultValue", "default_value", JSON),
    F("boolean", "required", "required", "", "false"),
    F("Map<String, Object>", "optionsSource", "options_source", JSON),
    F("int", "position", "position", "", "0"),
])

emit("com.landata.ai.domain", "AiProviderConfig", "ai_provider_config", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("String", "provider", "provider"),
    F("byte[]", "apiKeyCiphertext", "api_key_ciphertext"),
    F("String", "apiKeyKeyId", "api_key_key_id"),
    F("String", "defaultModelId", "default_model_id"),
    F("List<Object>", "modelParams", "model_params", JSON, "new ArrayList<>()"),
    F("Map<String, Object>", "settings", "settings", JSON, "new LinkedHashMap<>()"),
    F("boolean", "active", "is_active", "", "true"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "updatedAt", "updated_at"),
])

emit("com.landata.ai.domain", "AiConversation", "ai_conversation", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "userId", "user_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("String", "targetType", "target_type"),
    F("String", "title", "title"),
    F("String", "status", "status", "", '"open"'),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "updatedAt", "updated_at"),
])

emit("com.landata.ai.domain", "AiMessage", "ai_message", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "conversationId", "conversation_id"),
    F("String", "role", "role"),
    F("String", "content", "content"),
    F("Map<String, Object>", "generated", "generated", JSON),
    F("String", "validationStatus", "validation_status"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.ai.domain", "AiAgent", "ai_agent", [
    F("UUID", "id", "id", ID),
    F("UUID", "companyId", "company_id"),
    F("String", "provider", "provider"),
    F("String", "providerAgentId", "provider_agent_id"),
    F("String", "purpose", "purpose"),
    F("UUID", "conversationId", "conversation_id"),
    F("UUID", "scrapeJobId", "scrape_job_id"),
    F("String", "status", "status", "", '"creating"'),
    F("String", "modelId", "model_id"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "updatedAt", "updated_at"),
])

emit("com.landata.ai.domain", "AiRequest", "ai_request", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "userId", "user_id"),
    F("UUID", "conversationId", "conversation_id"),
    F("UUID", "datasourceId", "datasource_id"),
    F("String", "targetType", "target_type"),
    F("String", "status", "status", "", '"queued"'),
    F("String", "userPrompt", "user_prompt"),
    F("Map<String, Object>", "retrieval", "retrieval", JSON),
    F("Map<String, Object>", "plan", "plan", JSON),
    F("Map<String, Object>", "result", "result", JSON),
    F("Map<String, Object>", "validation", "validation", JSON),
    F("int", "retryCount", "retry_count", "", "0"),
    F("String", "errorCode", "error_code"),
    F("String", "errorMessage", "error_message"),
    F("String", "idempotencyKey", "idempotency_key"),
    F("Instant", "createdAt", "created_at"),
    F("Instant", "finishedAt", "finished_at"),
])

emit("com.landata.ai.domain", "AiRun", "ai_run", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "agentId", "agent_id"),
    F("String", "providerRunId", "provider_run_id"),
    F("String", "kind", "kind"),
    F("UUID", "aiRequestId", "ai_request_id"),
    F("UUID", "scrapeJobItemId", "scrape_job_item_id"),
    F("String", "promptTemplate", "prompt_template"),
    F("String", "promptVersion", "prompt_version"),
    F("String", "promptText", "prompt_text"),
    F("String", "promptHash", "prompt_hash"),
    F("String", "status", "status", "", '"creating"'),
    F("String", "resultText", "result_text"),
    F("Map<String, Object>", "parsedResult", "parsed_result", JSON),
    F("String", "errorCode", "error_code"),
    F("String", "errorMessage", "error_message"),
    F("Integer", "durationMs", "duration_ms"),
    F("Instant", "startedAt", "started_at"),
    F("Instant", "finishedAt", "finished_at"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.ai.domain", "AiUsage", "ai_usage", [
    F("UUID", "runId", "run_id", ID),
    F("UUID", "companyId", "company_id"),
    F("long", "inputTokens", "input_tokens", "", "0"),
    F("long", "outputTokens", "output_tokens", "", "0"),
    F("long", "cacheReadTokens", "cache_read_tokens", "", "0"),
    F("long", "cacheWriteTokens", "cache_write_tokens", "", "0"),
    F("long", "totalTokens", "total_tokens", "", "0"),
    F("BigDecimal", "costEstimate", "cost_estimate"),
    F("Instant", "recordedAt", "recorded_at"),
])

emit("com.landata.ai.domain", "AiRequestArtifact", "ai_request_artifact", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "requestId", "request_id"),
    F("String", "artifactType", "artifact_type"),
    F("UUID", "artifactId", "artifact_id"),
    F("String", "status", "status", "", '"draft"'),
])

emit("com.landata.ai.domain", "AiFeedback", "ai_feedback", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "userId", "user_id"),
    F("UUID", "aiRequestId", "ai_request_id"),
    F("short", "rating", "rating"),
    F("String", "comment", "comment"),
    F("String", "correctedSql", "corrected_sql"),
    F("boolean", "useForTraining", "use_for_training", "", "false"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.admin.domain", "AuditLog", "audit_log", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("UUID", "userId", "user_id"),
    F("String", "action", "action"),
    F("String", "entityType", "entity_type"),
    F("UUID", "entityId", "entity_id"),
    F("Map<String, Object>", "metadata", "metadata", JSON, "new LinkedHashMap<>()"),
    F("String", "ip", "ip"),
    F("String", "userAgent", "user_agent"),
    F("Instant", "createdAt", "created_at"),
])

emit("com.landata.admin.domain", "CompanyQuota", "company_quota", [
    F("UUID", "companyId", "company_id", ID),
    F("int", "aiRequestsPerMonth", "ai_requests_per_month", "", "200"),
    F("int", "queriesPerMinute", "queries_per_minute", "", "120"),
    F("int", "maxDatasources", "max_datasources", "", "5"),
    F("int", "maxRowsCap", "max_rows_cap", "", "100000"),
])

emit("com.landata.admin.domain", "CompanyUsageCounter", "company_usage_counter", [
    F("UUID", "companyId", "company_id", ID),
    F("LocalDate", "period", "period", ID),
    F("int", "aiRequests", "ai_requests", "", "0"),
    F("long", "aiTokens", "ai_tokens", "", "0"),
    F("long", "queries", "queries", "", "0"),
])

emit("com.landata.admin.domain", "EmbedToken", "embed_token", [
    F("UUID", "id", "id", ID, "UUID.randomUUID()"),
    F("UUID", "companyId", "company_id"),
    F("String", "resourceType", "resource_type"),
    F("UUID", "resourceId", "resource_id"),
    F("String", "tokenHash", "token_hash"),
    F("Map<String, Object>", "lockedParams", "locked_params", JSON, "new LinkedHashMap<>()"),
    F("Instant", "expiresAt", "expires_at"),
    F("Instant", "revokedAt", "revoked_at"),
    F("UUID", "createdBy", "created_by"),
    F("Instant", "createdAt", "created_at"),
])
