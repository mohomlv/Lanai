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
@Table(name = "ai_usage")
public class AiUsage  {
@Id

  @Column(name = "run_id")
  private UUID runId;

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "input_tokens")
  private long inputTokens = 0;

  @Column(name = "output_tokens")
  private long outputTokens = 0;

  @Column(name = "cache_read_tokens")
  private long cacheReadTokens = 0;

  @Column(name = "cache_write_tokens")
  private long cacheWriteTokens = 0;

  @Column(name = "total_tokens")
  private long totalTokens = 0;

  @Column(name = "cost_estimate")
  private BigDecimal costEstimate;

  @Column(name = "recorded_at")
  private Instant recordedAt;

  @PrePersist
  void prePersist() {
  }

  public UUID getRunId() { return runId; }
  public void setRunId(UUID runId) { this.runId = runId; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public long getInputTokens() { return inputTokens; }
  public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }

  public long getOutputTokens() { return outputTokens; }
  public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }

  public long getCacheReadTokens() { return cacheReadTokens; }
  public void setCacheReadTokens(long cacheReadTokens) { this.cacheReadTokens = cacheReadTokens; }

  public long getCacheWriteTokens() { return cacheWriteTokens; }
  public void setCacheWriteTokens(long cacheWriteTokens) { this.cacheWriteTokens = cacheWriteTokens; }

  public long getTotalTokens() { return totalTokens; }
  public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }

  public BigDecimal getCostEstimate() { return costEstimate; }
  public void setCostEstimate(BigDecimal costEstimate) { this.costEstimate = costEstimate; }

  public Instant getRecordedAt() { return recordedAt; }
  public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

}
