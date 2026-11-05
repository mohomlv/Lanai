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
@Table(name = "ai_run")
public class AiRun  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "agent_id")
  private UUID agentId;

  @Column(name = "provider_run_id")
  private String providerRunId;

  @Column(name = "kind")
  private String kind;

  @Column(name = "ai_request_id")
  private UUID aiRequestId;

  @Column(name = "scrape_job_item_id")
  private UUID scrapeJobItemId;

  @Column(name = "prompt_template")
  private String promptTemplate;

  @Column(name = "prompt_version")
  private String promptVersion;

  @Column(name = "prompt_text")
  private String promptText;

  @Column(name = "prompt_hash")
  private String promptHash;

  @Column(name = "status")
  private String status = "creating";

  @Column(name = "result_text")
  private String resultText;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "parsed_result")
  private Map<String, Object> parsedResult;

  @Column(name = "error_code")
  private String errorCode;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "duration_ms")
  private Integer durationMs;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "created_at")
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getAgentId() { return agentId; }
  public void setAgentId(UUID agentId) { this.agentId = agentId; }

  public String getProviderRunId() { return providerRunId; }
  public void setProviderRunId(String providerRunId) { this.providerRunId = providerRunId; }

  public String getKind() { return kind; }
  public void setKind(String kind) { this.kind = kind; }

  public UUID getAiRequestId() { return aiRequestId; }
  public void setAiRequestId(UUID aiRequestId) { this.aiRequestId = aiRequestId; }

  public UUID getScrapeJobItemId() { return scrapeJobItemId; }
  public void setScrapeJobItemId(UUID scrapeJobItemId) { this.scrapeJobItemId = scrapeJobItemId; }

  public String getPromptTemplate() { return promptTemplate; }
  public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }

  public String getPromptVersion() { return promptVersion; }
  public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

  public String getPromptText() { return promptText; }
  public void setPromptText(String promptText) { this.promptText = promptText; }

  public String getPromptHash() { return promptHash; }
  public void setPromptHash(String promptHash) { this.promptHash = promptHash; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getResultText() { return resultText; }
  public void setResultText(String resultText) { this.resultText = resultText; }

  public Map<String, Object> getParsedResult() { return parsedResult; }
  public void setParsedResult(Map<String, Object> parsedResult) { this.parsedResult = parsedResult; }

  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

  public Integer getDurationMs() { return durationMs; }
  public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }

  public Instant getStartedAt() { return startedAt; }
  public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

  public Instant getFinishedAt() { return finishedAt; }
  public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
