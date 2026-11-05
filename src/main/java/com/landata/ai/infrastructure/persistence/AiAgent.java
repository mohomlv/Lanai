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
@Table(name = "ai_agent")
public class AiAgent  {
@Id

  @Column(name = "id")
  private UUID id;

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "provider")
  private String provider;

  @Column(name = "provider_agent_id")
  private String providerAgentId;

  @Column(name = "purpose")
  private String purpose;

  @Column(name = "conversation_id")
  private UUID conversationId;

  @Column(name = "scrape_job_id")
  private UUID scrapeJobId;

  @Column(name = "status")
  private String status = "creating";

  @Column(name = "model_id")
  private String modelId;

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

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }

  public String getProviderAgentId() { return providerAgentId; }
  public void setProviderAgentId(String providerAgentId) { this.providerAgentId = providerAgentId; }

  public String getPurpose() { return purpose; }
  public void setPurpose(String purpose) { this.purpose = purpose; }

  public UUID getConversationId() { return conversationId; }
  public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

  public UUID getScrapeJobId() { return scrapeJobId; }
  public void setScrapeJobId(UUID scrapeJobId) { this.scrapeJobId = scrapeJobId; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getModelId() { return modelId; }
  public void setModelId(String modelId) { this.modelId = modelId; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
