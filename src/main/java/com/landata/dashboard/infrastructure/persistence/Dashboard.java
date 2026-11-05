package com.landata.dashboard.infrastructure.persistence;

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
@Table(name = "dashboard")
public class Dashboard  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "collection_id")
  private UUID collectionId;

  @Column(name = "name")
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "auto_refresh_seconds")
  private Integer autoRefreshSeconds;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "settings")
  private Map<String, Object> settings = new LinkedHashMap<>();

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = Instant.now();
    if (settings == null) settings = new LinkedHashMap<>();
    if (name == null || name.isBlank()) name = "New dashboard";
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getCollectionId() { return collectionId; }
  public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public Integer getAutoRefreshSeconds() { return autoRefreshSeconds; }
  public void setAutoRefreshSeconds(Integer autoRefreshSeconds) { this.autoRefreshSeconds = autoRefreshSeconds; }

  public Map<String, Object> getSettings() { return settings; }
  public void setSettings(Map<String, Object> settings) { this.settings = settings; }

  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public Instant getArchivedAt() { return archivedAt; }
  public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }

}
