package com.landata.admin.infrastructure.persistence;

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
@Table(name = "audit_log")
public class AuditLog  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "action")
  private String action;

  @Column(name = "entity_type")
  private String entityType;

  @Column(name = "entity_id")
  private UUID entityId;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "metadata")
  private Map<String, Object> metadata = new LinkedHashMap<>();

  @Column(name = "ip")
  private String ip;

  @Column(name = "user_agent")
  private String userAgent;

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

  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }

  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }

  public String getEntityType() { return entityType; }
  public void setEntityType(String entityType) { this.entityType = entityType; }

  public UUID getEntityId() { return entityId; }
  public void setEntityId(UUID entityId) { this.entityId = entityId; }

  public Map<String, Object> getMetadata() { return metadata; }
  public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

  public String getIp() { return ip; }
  public void setIp(String ip) { this.ip = ip; }

  public String getUserAgent() { return userAgent; }
  public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
