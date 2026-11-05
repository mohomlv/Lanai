package com.landata.viz.infrastructure.persistence;

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
@Table(name = "chart_template")
public class ChartTemplate  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "[key]")
  private String key;

  @Column(name = "name")
  private String name;

  @Column(name = "version")
  private int version = 1;

  @Column(name = "description")
  private String description;

  @Column(name = "markdown")
  private String markdown;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "viz_schema")
  private Map<String, Object> vizSchema = new LinkedHashMap<>();

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "data_requirements")
  private Map<String, Object> dataRequirements = new LinkedHashMap<>();

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "example_viz_config")
  private Map<String, Object> exampleVizConfig;

  @Column(name = "example_sql")
  private String exampleSql;

  @Column(name = "source")
  private String source = "file";

  @Column(name = "content_hash")
  private String contentHash;

  @Column(name = "is_active")
  private boolean active = true;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public String getKey() { return key; }
  public void setKey(String key) { this.key = key; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public int getVersion() { return version; }
  public void setVersion(int version) { this.version = version; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public String getMarkdown() { return markdown; }
  public void setMarkdown(String markdown) { this.markdown = markdown; }

  public Map<String, Object> getVizSchema() { return vizSchema; }
  public void setVizSchema(Map<String, Object> vizSchema) { this.vizSchema = vizSchema; }

  public Map<String, Object> getDataRequirements() { return dataRequirements; }
  public void setDataRequirements(Map<String, Object> dataRequirements) { this.dataRequirements = dataRequirements; }

  public Map<String, Object> getExampleVizConfig() { return exampleVizConfig; }
  public void setExampleVizConfig(Map<String, Object> exampleVizConfig) { this.exampleVizConfig = exampleVizConfig; }

  public String getExampleSql() { return exampleSql; }
  public void setExampleSql(String exampleSql) { this.exampleSql = exampleSql; }

  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }

  public String getContentHash() { return contentHash; }
  public void setContentHash(String contentHash) { this.contentHash = contentHash; }

  public boolean getActive() { return active; }
  public void setActive(boolean active) { this.active = active; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
