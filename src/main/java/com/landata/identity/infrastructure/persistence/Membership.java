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
@Table(name = "membership")
public class Membership  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "role")
  private String role;

  @Column(name = "status")
  private String status = "active";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "branch_ids")
  private List<Integer> branchIds = new ArrayList<>();

  @Column(name = "created_at")
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (branchIds == null) branchIds = new ArrayList<>();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }

  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public List<Integer> getBranchIds() { return branchIds == null ? List.of() : branchIds; }
  public void setBranchIds(List<Integer> branchIds) { this.branchIds = branchIds == null ? new ArrayList<>() : branchIds; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
