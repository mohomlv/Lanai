package com.landata.ai.infrastructure.persistence;

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
@Table(name = "ai_request_artifact")
public class AiRequestArtifact  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "request_id")
  private UUID requestId;

  @Column(name = "artifact_type")
  private String artifactType;

  @Column(name = "artifact_id")
  private UUID artifactId;

  @Column(name = "status")
  private String status = "draft";

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getRequestId() { return requestId; }
  public void setRequestId(UUID requestId) { this.requestId = requestId; }

  public String getArtifactType() { return artifactType; }
  public void setArtifactType(String artifactType) { this.artifactType = artifactType; }

  public UUID getArtifactId() { return artifactId; }
  public void setArtifactId(UUID artifactId) { this.artifactId = artifactId; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

}
