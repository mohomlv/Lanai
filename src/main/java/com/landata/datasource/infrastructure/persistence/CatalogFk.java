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
@Table(name = "catalog_fk")
public class CatalogFk  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "datasource_id")
  private UUID datasourceId;

  @Column(name = "constraint_name")
  private String constraintName;

  @Column(name = "ordinal")
  private int ordinal = 1;

  @Column(name = "from_column_id")
  private UUID fromColumnId;

  @Column(name = "to_column_id")
  private UUID toColumnId;

  @Column(name = "is_inferred")
  private boolean inferred = false;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getDatasourceId() { return datasourceId; }
  public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }

  public String getConstraintName() { return constraintName; }
  public void setConstraintName(String constraintName) { this.constraintName = constraintName; }

  public int getOrdinal() { return ordinal; }
  public void setOrdinal(int ordinal) { this.ordinal = ordinal; }

  public UUID getFromColumnId() { return fromColumnId; }
  public void setFromColumnId(UUID fromColumnId) { this.fromColumnId = fromColumnId; }

  public UUID getToColumnId() { return toColumnId; }
  public void setToColumnId(UUID toColumnId) { this.toColumnId = toColumnId; }

  public boolean getInferred() { return inferred; }
  public void setInferred(boolean inferred) { this.inferred = inferred; }

}
