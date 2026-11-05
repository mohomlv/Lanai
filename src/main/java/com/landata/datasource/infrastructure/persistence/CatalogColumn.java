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
@Table(name = "catalog_column")
public class CatalogColumn  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "table_id")
  private UUID tableId;

  @Column(name = "name")
  private String name;

  @Column(name = "ordinal")
  private int ordinal;

  @Column(name = "data_type")
  private String dataType;

  @Column(name = "normalized_type")
  private String normalizedType;

  @Column(name = "semantic_type")
  private String semanticType;

  @Column(name = "is_nullable")
  private boolean nullable = true;

  @Column(name = "is_pk")
  private boolean pk = false;

  @Column(name = "is_pii")
  private boolean pii = false;

  @Column(name = "exclude_from_samples")
  private boolean excludeFromSamples = false;

  @Column(name = "visible")
  private boolean visible = true;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "user_note")
  private String userNote;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "sample_values")
  private List<Object> sampleValues;

  @Column(name = "distinct_count")
  private Long distinctCount;

  @Column(name = "null_fraction")
  private BigDecimal nullFraction;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getTableId() { return tableId; }
  public void setTableId(UUID tableId) { this.tableId = tableId; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public int getOrdinal() { return ordinal; }
  public void setOrdinal(int ordinal) { this.ordinal = ordinal; }

  public String getDataType() { return dataType; }
  public void setDataType(String dataType) { this.dataType = dataType; }

  public String getNormalizedType() { return normalizedType; }
  public void setNormalizedType(String normalizedType) { this.normalizedType = normalizedType; }

  public String getSemanticType() { return semanticType; }
  public void setSemanticType(String semanticType) { this.semanticType = semanticType; }

  public boolean getNullable() { return nullable; }
  public void setNullable(boolean nullable) { this.nullable = nullable; }

  public boolean getPk() { return pk; }
  public void setPk(boolean pk) { this.pk = pk; }

  public boolean getPii() { return pii; }
  public void setPii(boolean pii) { this.pii = pii; }

  public boolean getExcludeFromSamples() { return excludeFromSamples; }
  public void setExcludeFromSamples(boolean excludeFromSamples) { this.excludeFromSamples = excludeFromSamples; }

  public boolean getVisible() { return visible; }
  public void setVisible(boolean visible) { this.visible = visible; }

  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }

  public String getUserNote() { return userNote; }
  public void setUserNote(String userNote) { this.userNote = userNote; }

  public List<Object> getSampleValues() { return sampleValues; }
  public void setSampleValues(List<Object> sampleValues) { this.sampleValues = sampleValues; }

  public Long getDistinctCount() { return distinctCount; }
  public void setDistinctCount(Long distinctCount) { this.distinctCount = distinctCount; }

  public BigDecimal getNullFraction() { return nullFraction; }
  public void setNullFraction(BigDecimal nullFraction) { this.nullFraction = nullFraction; }

}
