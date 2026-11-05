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
@Table(name = "api_key")
public class ApiKey  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "name")
  private String name;

  @Column(name = "key_prefix")
  private String keyPrefix;

  @Column(name = "key_hash")
  private String keyHash;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "scopes")
  private List<String> scopes = new ArrayList<>();

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

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

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getKeyPrefix() { return keyPrefix; }
  public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

  public String getKeyHash() { return keyHash; }
  public void setKeyHash(String keyHash) { this.keyHash = keyHash; }

  public List<String> getScopes() { return scopes; }
  public void setScopes(List<String> scopes) { this.scopes = scopes; }

  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

  public Instant getLastUsedAt() { return lastUsedAt; }
  public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

  public Instant getRevokedAt() { return revokedAt; }
  public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
