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
@Table(name = "ai_message")
public class AiMessage  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "conversation_id")
  private UUID conversationId;

  @Column(name = "role")
  private String role;

  @Column(name = "content")
  private String content;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "generated")
  private Map<String, Object> generated;

  @Column(name = "validation_status")
  private String validationStatus;

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

  public UUID getConversationId() { return conversationId; }
  public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }

  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }

  public Map<String, Object> getGenerated() { return generated; }
  public void setGenerated(Map<String, Object> generated) { this.generated = generated; }

  public String getValidationStatus() { return validationStatus; }
  public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
