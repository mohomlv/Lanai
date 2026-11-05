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
@Table(name = "scrape_job_item")
public class ScrapeJobItem  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "job_id")
  private UUID jobId;

  @Column(name = "kind")
  private String kind;

  @Column(name = "table_id")
  private UUID tableId;

  @Column(name = "status")
  private String status = "queued";

  @Column(name = "attempts")
  private int attempts = 0;

  @Column(name = "error")
  private String error;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (updatedAt == null) updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getJobId() { return jobId; }
  public void setJobId(UUID jobId) { this.jobId = jobId; }

  public String getKind() { return kind; }
  public void setKind(String kind) { this.kind = kind; }

  public UUID getTableId() { return tableId; }
  public void setTableId(UUID tableId) { this.tableId = tableId; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }

  public String getError() { return error; }
  public void setError(String error) { this.error = error; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
