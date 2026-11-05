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
@Table(name = "invitation")
public class Invitation  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "email")
  private String email;

  @Column(name = "role")
  private String role;

  @Column(name = "token_hash")
  private String tokenHash;

  @Column(name = "invited_by")
  private UUID invitedBy;

  @Column(name = "status")
  private String status = "pending";

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

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

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }

  public String getTokenHash() { return tokenHash; }
  public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

  public UUID getInvitedBy() { return invitedBy; }
  public void setInvitedBy(UUID invitedBy) { this.invitedBy = invitedBy; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

  public Instant getAcceptedAt() { return acceptedAt; }
  public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
