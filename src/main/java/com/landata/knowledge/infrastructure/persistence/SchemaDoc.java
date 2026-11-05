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
@Table(name = "schema_doc")
public class SchemaDoc  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "datasource_id")
  private UUID datasourceId;

  @Column(name = "table_id")
  private UUID tableId;

  @Column(name = "kind")
  private String kind;

  @Column(name = "title")
  private String title;

  @Column(name = "markdown")
  private String markdown;

  @Column(name = "content_hash")
  private String contentHash;

  @Column(name = "version")
  private int version = 1;

  @Column(name = "source")
  private String source;

  @Column(name = "is_current")
  private boolean current = true;

  @Column(name = "review_status")
  private String reviewStatus = "accepted";

  @Column(name = "ai_run_id")
  private UUID aiRunId;

  @Column(name = "created_by")
  private UUID createdBy;

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

  public UUID getTableId() { return tableId; }
  public void setTableId(UUID tableId) { this.tableId = tableId; }

  public String getKind() { return kind; }
  public void setKind(String kind) { this.kind = kind; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getMarkdown() { return markdown; }
  public void setMarkdown(String markdown) { this.markdown = markdown; }

  public String getContentHash() { return contentHash; }
  public void setContentHash(String contentHash) { this.contentHash = contentHash; }

  public int getVersion() { return version; }
  public void setVersion(int version) { this.version = version; }

  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }

  public boolean getCurrent() { return current; }
  public void setCurrent(boolean current) { this.current = current; }

  public String getReviewStatus() { return reviewStatus; }
  public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

  public UUID getAiRunId() { return aiRunId; }
  public void setAiRunId(UUID aiRunId) { this.aiRunId = aiRunId; }

  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
