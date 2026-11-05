package com.landata.identity.infrastructure.persistence;

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
@Table(name = "company")
public class Company  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "name")
  private String name;

  @Column(name = "slug")
  private String slug;

  @Column(name = "owner_id")
  private UUID ownerId;

  @Column(name = "[plan]")
  private String plan = "free";

  @Column(name = "status")
  private String status = "active";

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "settings")
  private Map<String, Object> settings = new LinkedHashMap<>();

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }

  public UUID getOwnerId() { return ownerId; }
  public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

  public String getPlan() { return plan; }
  public void setPlan(String plan) { this.plan = plan; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public Map<String, Object> getSettings() { return settings; }
  public void setSettings(Map<String, Object> settings) { this.settings = settings; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public Instant getDeletedAt() { return deletedAt; }
  public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

}
