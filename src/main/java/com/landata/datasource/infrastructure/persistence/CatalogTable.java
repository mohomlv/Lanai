package com.landata.datasource.infrastructure.persistence;

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
@Table(name = "catalog_table")
public class CatalogTable  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "datasource_id")
  private UUID datasourceId;

  @Column(name = "schema_name")
  private String schemaName;

  @Column(name = "table_name")
  private String tableName;

  @Column(name = "table_type")
  private String tableType = "table";

  @Column(name = "row_estimate")
  private Long rowEstimate;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "user_note")
  private String userNote;

  @Column(name = "visible")
  private boolean visible = true;

  @Column(name = "removed_at")
  private Instant removedAt;

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

  public UUID getDatasourceId() { return datasourceId; }
  public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }

  public String getSchemaName() { return schemaName; }
  public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

  public String getTableName() { return tableName; }
  public void setTableName(String tableName) { this.tableName = tableName; }

  public String getTableType() { return tableType; }
  public void setTableType(String tableType) { this.tableType = tableType; }

  public Long getRowEstimate() { return rowEstimate; }
  public void setRowEstimate(Long rowEstimate) { this.rowEstimate = rowEstimate; }

  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }

  public String getUserNote() { return userNote; }
  public void setUserNote(String userNote) { this.userNote = userNote; }

  public boolean getVisible() { return visible; }
  public void setVisible(boolean visible) { this.visible = visible; }

  public Instant getRemovedAt() { return removedAt; }
  public void setRemovedAt(Instant removedAt) { this.removedAt = removedAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
