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
@Table(name = "saved_question")
public class SavedQuestion  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "datasource_id")
  private UUID datasourceId;

  @Column(name = "collection_id")
  private UUID collectionId;

  @Column(name = "name")
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "current_version_id")
  private UUID currentVersionId;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "is_parameter")
  private boolean parameterQuestion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "parameter_fields")
  private Map<String, Object> parameterFields;

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

  public UUID getDatasourceId() { return datasourceId; }
  public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }

  public UUID getCollectionId() { return collectionId; }
  public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public UUID getCurrentVersionId() { return currentVersionId; }
  public void setCurrentVersionId(UUID currentVersionId) { this.currentVersionId = currentVersionId; }

  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public Instant getArchivedAt() { return archivedAt; }
  public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }

  public boolean isParameterQuestion() { return parameterQuestion; }
  public void setParameterQuestion(boolean parameterQuestion) { this.parameterQuestion = parameterQuestion; }

  public Map<String, Object> getParameterFields() { return parameterFields; }
  public void setParameterFields(Map<String, Object> parameterFields) { this.parameterFields = parameterFields; }

}
