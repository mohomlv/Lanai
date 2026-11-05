from pathlib import Path

ROOT = Path(r"d:\Smart-Dashboard\landata\src\main\java")

repos = {
"com.landata.identity.domain": [
("UserAccountRepository", "UserAccount", "UUID", [
    "Optional<UserAccount> findByEmailIgnoreCase(String email);",
]),
("CompanyRepository", "Company", "UUID", [
    "Optional<Company> findByIdAndDeletedAtIsNull(UUID id);",
    "Optional<Company> findBySlugAndDeletedAtIsNull(String slug);",
]),
("MembershipRepository", "Membership", "UUID", [
    "Optional<Membership> findByCompanyIdAndUserId(UUID companyId, UUID userId);",
    "java.util.List<Membership> findByUserIdAndStatus(UUID userId, String status);",
    "java.util.List<Membership> findByCompanyIdAndStatus(UUID companyId, String status);",
]),
("InvitationRepository", "Invitation", "UUID", [
    "Optional<Invitation> findByTokenHash(String tokenHash);",
    "Optional<Invitation> findByIdAndCompanyId(UUID id, UUID companyId);",
    "java.util.List<Invitation> findByCompanyIdAndStatus(UUID companyId, String status);",
]),
("AuthTokenRepository", "AuthToken", "UUID", [
    "Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, String purpose);",
]),
("RefreshTokenRepository", "RefreshToken", "UUID", [
    "Optional<RefreshToken> findByTokenHash(String tokenHash);",
    "java.util.List<RefreshToken> findByFamilyId(UUID familyId);",
]),
("ApiKeyRepository", "ApiKey", "UUID", [
    "java.util.List<ApiKey> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);",
    "Optional<ApiKey> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
],
"com.landata.datasource.domain": [
("DatasourceRepository", "Datasource", "UUID", [
    "java.util.List<Datasource> findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(UUID companyId);",
    "Optional<Datasource> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);",
    "long countByCompanyIdAndDeletedAtIsNull(UUID companyId);",
]),
("DatasourcePermissionRepository", "DatasourcePermission", "UUID", [
    "java.util.List<DatasourcePermission> findByDatasourceIdAndCompanyId(UUID datasourceId, UUID companyId);",
    "Optional<DatasourcePermission> findByDatasourceIdAndRole(UUID datasourceId, String role);",
]),
("CatalogTableRepository", "CatalogTable", "UUID", [
    "java.util.List<CatalogTable> findByDatasourceIdAndCompanyId(UUID datasourceId, UUID companyId);",
    "Optional<CatalogTable> findByIdAndCompanyId(UUID id, UUID companyId);",
    "Optional<CatalogTable> findByDatasourceIdAndSchemaNameAndTableName(UUID datasourceId, String schemaName, String tableName);",
]),
("CatalogColumnRepository", "CatalogColumn", "UUID", [
    "java.util.List<CatalogColumn> findByTableIdOrderByOrdinalAsc(UUID tableId);",
    "Optional<CatalogColumn> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
("CatalogFkRepository", "CatalogFk", "UUID", [
    "java.util.List<CatalogFk> findByDatasourceIdAndCompanyId(UUID datasourceId, UUID companyId);",
    "Optional<CatalogFk> findByIdAndCompanyId(UUID id, UUID companyId);",
    "void deleteByDatasourceIdAndInferredTrue(UUID datasourceId);",
]),
("SchemaSyncScheduleRepository", "SchemaSyncSchedule", "UUID", []),
],
"com.landata.knowledge.domain": [
("ScrapeJobRepository", "ScrapeJob", "UUID", [
    "Optional<ScrapeJob> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
("ScrapeJobItemRepository", "ScrapeJobItem", "UUID", [
    "java.util.List<ScrapeJobItem> findByJobIdOrderByUpdatedAtAsc(UUID jobId);",
    "Optional<ScrapeJobItem> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
("SchemaDocRepository", "SchemaDoc", "UUID", [
    "java.util.List<SchemaDoc> findByDatasourceIdAndCompanyIdAndCurrentTrue(UUID datasourceId, UUID companyId);",
    "Optional<SchemaDoc> findByIdAndCompanyId(UUID id, UUID companyId);",
    "java.util.List<SchemaDoc> findByDatasourceIdAndTableIdAndKindOrderByVersionDesc(UUID datasourceId, UUID tableId, String kind);",
    "Optional<SchemaDoc> findFirstByDatasourceIdAndKindAndTableIdIsNullAndCurrentTrue(UUID datasourceId, String kind);",
    "Optional<SchemaDoc> findFirstByDatasourceIdAndTableIdAndKindAndCurrentTrue(UUID datasourceId, UUID tableId, String kind);",
]),
("GlossaryTermRepository", "GlossaryTerm", "UUID", [
    "java.util.List<GlossaryTerm> findByCompanyIdAndDatasourceId(UUID companyId, UUID datasourceId);",
    "Optional<GlossaryTerm> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
("MetricDefinitionRepository", "MetricDefinition", "UUID", [
    "java.util.List<MetricDefinition> findByCompanyIdAndDatasourceId(UUID companyId, UUID datasourceId);",
    "Optional<MetricDefinition> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
],
"com.landata.viz.domain": [
("ChartTemplateRepository", "ChartTemplate", "UUID", [
    "java.util.List<ChartTemplate> findByCompanyIdIsNullAndActiveTrueOrderByKeyAsc();",
    "Optional<ChartTemplate> findFirstByCompanyIdIsNullAndKeyAndActiveTrue(String key);",
]),
],
"com.landata.query.domain": [
("CollectionFolderRepository", "CollectionFolder", "UUID", [
    "java.util.List<CollectionFolder> findByCompanyIdAndArchivedAtIsNullOrderByNameAsc(UUID companyId);",
    "Optional<CollectionFolder> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
("SavedQuestionRepository", "SavedQuestion", "UUID", [
    "java.util.List<SavedQuestion> findByCompanyIdAndArchivedAtIsNullOrderByUpdatedAtDesc(UUID companyId);",
    "Optional<SavedQuestion> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
("QuestionVersionRepository", "QuestionVersion", "UUID", [
    "java.util.List<QuestionVersion> findByQuestionIdOrderByVersionNoDesc(UUID questionId);",
    "Optional<QuestionVersion> findByQuestionIdAndVersionNo(UUID questionId, int versionNo);",
]),
("QueryRunRepository", "QueryRun", "UUID", [
    "java.util.List<QueryRun> findByCompanyIdOrderByStartedAtDesc(UUID companyId);",
    "Optional<QueryRun> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
],
"com.landata.dashboard.domain": [
("DashboardRepository", "Dashboard", "UUID", [
    "java.util.List<Dashboard> findByCompanyIdAndArchivedAtIsNullOrderByUpdatedAtDesc(UUID companyId);",
    "Optional<Dashboard> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
("DashboardTabRepository", "DashboardTab", "UUID", [
    "java.util.List<DashboardTab> findByDashboardIdOrderByPositionAsc(UUID dashboardId);",
    "void deleteByDashboardId(UUID dashboardId);",
]),
("DashboardCardRepository", "DashboardCard", "UUID", [
    "java.util.List<DashboardCard> findByDashboardId(UUID dashboardId);",
    "Optional<DashboardCard> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
("DashboardParameterRepository", "DashboardParameter", "UUID", [
    "java.util.List<DashboardParameter> findByDashboardIdOrderByPositionAsc(UUID dashboardId);",
    "void deleteByDashboardId(UUID dashboardId);",
]),
],
"com.landata.ai.domain": [
("AiProviderConfigRepository", "AiProviderConfig", "UUID", [
    "Optional<AiProviderConfig> findFirstByCompanyIdAndActiveTrue(UUID companyId);",
    "Optional<AiProviderConfig> findFirstByCompanyIdIsNullAndActiveTrue();",
]),
("AiConversationRepository", "AiConversation", "UUID", [
    "Optional<AiConversation> findByIdAndCompanyId(UUID id, UUID companyId);",
]),
("AiMessageRepository", "AiMessage", "UUID", [
    "java.util.List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);",
]),
("AiAgentRepository", "AiAgent", "UUID", [
    "Optional<AiAgent> findByIdAndCompanyId(UUID id, UUID companyId);",
    "Optional<AiAgent> findByProviderAgentId(String providerAgentId);",
]),
("AiRequestRepository", "AiRequest", "UUID", [
    "Optional<AiRequest> findByIdAndCompanyId(UUID id, UUID companyId);",
    "Optional<AiRequest> findByCompanyIdAndIdempotencyKey(UUID companyId, String idempotencyKey);",
]),
("AiRunRepository", "AiRun", "UUID", []),
("AiUsageRepository", "AiUsage", "UUID", []),
("AiRequestArtifactRepository", "AiRequestArtifact", "UUID", [
    "java.util.List<AiRequestArtifact> findByRequestId(UUID requestId);",
]),
("AiFeedbackRepository", "AiFeedback", "UUID", []),
],
"com.landata.admin.domain": [
("AuditLogRepository", "AuditLog", "UUID", [
    "org.springframework.data.domain.Page<AuditLog> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, org.springframework.data.domain.Pageable pageable);",
]),
("CompanyQuotaRepository", "CompanyQuota", "UUID", []),
("CompanyUsageCounterRepository", "CompanyUsageCounter", "CompanyUsageKey", [
    "Optional<CompanyUsageCounter> findByCompanyIdAndPeriod(UUID companyId, java.time.LocalDate period);",
]),
("EmbedTokenRepository", "EmbedToken", "UUID", [
    "java.util.List<EmbedToken> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);",
]),
],
}

for pkg, items in repos.items():
    for name, entity, idtype, methods in items:
        extra = ""
        if idtype == "CompanyUsageKey":
            extra = ""
        text = f"""package {pkg};

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface {name} extends JpaRepository<{entity}, {idtype}> {{
"""
        for m in methods:
            text += "  " + m + "\n"
        text += "}\n"
        path = ROOT.joinpath(*pkg.split("."), name + ".java")
        path.write_text(text, encoding="utf-8")
        print(path.name)
