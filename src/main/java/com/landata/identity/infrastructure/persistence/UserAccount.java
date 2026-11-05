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
@Table(name = "user_account")
public class UserAccount  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "email")
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "username")
  private String username;

  @Column(name = "email_verified")
  private boolean emailVerified = false;

  @Column(name = "status")
  private String status = "active";

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

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

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }

  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }

  public boolean getEmailVerified() { return emailVerified; }
  public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public Instant getLastLoginAt() { return lastLoginAt; }
  public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
