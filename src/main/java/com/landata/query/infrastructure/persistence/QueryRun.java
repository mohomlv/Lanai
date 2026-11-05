package com.landata.query.infrastructure.persistence;

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


@Entity
@Table(name = "query_run")
public class QueryRun  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "datasource_id")
  private UUID datasourceId;

  @Column(name = "question_id")
  private UUID questionId;

  @Column(name = "dashboard_id")
  private UUID dashboardId;

  @Column(name = "ai_request_id")
  private UUID aiRequestId;

  @Column(name = "source")
  private String source;

  @Column(name = "sql_hash")
  private String sqlHash;

  @Column(name = "params_hash")
  private String paramsHash;

  @Column(name = "compiled_sql")
  private String compiledSql;

  @Column(name = "status")
  private String status;

  @Column(name = "row_count")
  private Integer rowCount;

  @Column(name = "duration_ms")
  private Integer durationMs;

  @Column(name = "cache_hit")
  private boolean cacheHit = false;

  @Column(name = "error_code")
  private String errorCode;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "started_at")
  private Instant startedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (startedAt == null) startedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }

  public UUID getDatasourceId() { return datasourceId; }
  public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }

  public UUID getQuestionId() { return questionId; }
  public void setQuestionId(UUID questionId) { this.questionId = questionId; }

  public UUID getDashboardId() { return dashboardId; }
  public void setDashboardId(UUID dashboardId) { this.dashboardId = dashboardId; }

  public UUID getAiRequestId() { return aiRequestId; }
  public void setAiRequestId(UUID aiRequestId) { this.aiRequestId = aiRequestId; }

  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }

  public String getSqlHash() { return sqlHash; }
  public void setSqlHash(String sqlHash) { this.sqlHash = sqlHash; }

  public String getParamsHash() { return paramsHash; }
  public void setParamsHash(String paramsHash) { this.paramsHash = paramsHash; }

  public String getCompiledSql() { return compiledSql; }
  public void setCompiledSql(String compiledSql) { this.compiledSql = compiledSql; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public Integer getRowCount() { return rowCount; }
  public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }

  public Integer getDurationMs() { return durationMs; }
  public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }

  public boolean getCacheHit() { return cacheHit; }
  public void setCacheHit(boolean cacheHit) { this.cacheHit = cacheHit; }

  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

  public Instant getStartedAt() { return startedAt; }
  public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

}
