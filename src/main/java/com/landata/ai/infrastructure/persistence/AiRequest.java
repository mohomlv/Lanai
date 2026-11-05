package com.landata.ai.infrastructure.persistence;

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
@Table(name = "ai_request")
public class AiRequest  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "conversation_id")
  private UUID conversationId;

  @Column(name = "datasource_id")
  private UUID datasourceId;

  @Column(name = "provider_config_id")
  private UUID providerConfigId;

  @Column(name = "target_type")
  private String targetType;

  @Column(name = "status")
  private String status = "queued";

  @Column(name = "user_prompt")
  private String userPrompt;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "retrieval")
  private Map<String, Object> retrieval;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "[plan]")
  private Map<String, Object> plan;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "result")
  private Map<String, Object> result;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "validation")
  private Map<String, Object> validation;

  @Column(name = "retry_count")
  private int retryCount = 0;

  @Column(name = "error_code")
  private String errorCode;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }

  public UUID getConversationId() { return conversationId; }
  public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

  public UUID getDatasourceId() { return datasourceId; }
  public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }

  public UUID getProviderConfigId() { return providerConfigId; }
  public void setProviderConfigId(UUID providerConfigId) { this.providerConfigId = providerConfigId; }

  public String getTargetType() { return targetType; }
  public void setTargetType(String targetType) { this.targetType = targetType; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getUserPrompt() { return userPrompt; }
  public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }

  public Map<String, Object> getRetrieval() { return retrieval; }
  public void setRetrieval(Map<String, Object> retrieval) { this.retrieval = retrieval; }

  public Map<String, Object> getPlan() { return plan; }
  public void setPlan(Map<String, Object> plan) { this.plan = plan; }

  public Map<String, Object> getResult() { return result; }
  public void setResult(Map<String, Object> result) { this.result = result; }

  public Map<String, Object> getValidation() { return validation; }
  public void setValidation(Map<String, Object> validation) { this.validation = validation; }

  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getFinishedAt() { return finishedAt; }
  public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

}
