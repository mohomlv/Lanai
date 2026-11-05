package com.landata.knowledge.infrastructure.persistence;

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
@Table(name = "scrape_job")
public class ScrapeJob  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "datasource_id")
  private UUID datasourceId;

  @Column(name = "status")
  private String status = "queued";

  @Column(name = "[trigger]")
  private String trigger;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "scope")
  private Map<String, Object> scope;

  @Column(name = "total_items")
  private int totalItems = 0;

  @Column(name = "done_items")
  private int doneItems = 0;

  @Column(name = "failed_items")
  private int failedItems = 0;

  @Column(name = "requested_by")
  private UUID requestedBy;

  @Column(name = "error")
  private String error;

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

  public UUID getDatasourceId() { return datasourceId; }
  public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getTrigger() { return trigger; }
  public void setTrigger(String trigger) { this.trigger = trigger; }

  public Map<String, Object> getScope() { return scope; }
  public void setScope(Map<String, Object> scope) { this.scope = scope; }

  public int getTotalItems() { return totalItems; }
  public void setTotalItems(int totalItems) { this.totalItems = totalItems; }

  public int getDoneItems() { return doneItems; }
  public void setDoneItems(int doneItems) { this.doneItems = doneItems; }

  public int getFailedItems() { return failedItems; }
  public void setFailedItems(int failedItems) { this.failedItems = failedItems; }

  public UUID getRequestedBy() { return requestedBy; }
  public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }

  public String getError() { return error; }
  public void setError(String error) { this.error = error; }

  public Instant getStartedAt() { return startedAt; }
  public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

  public Instant getFinishedAt() { return finishedAt; }
  public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
