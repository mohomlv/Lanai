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
@Table(name = "ai_feedback")
public class AiFeedback  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "ai_request_id")
  private UUID aiRequestId;

  @Column(name = "rating")
  private short rating;

  @Column(name = "comment")
  private String comment;

  @Column(name = "corrected_sql")
  private String correctedSql;

  @Column(name = "use_for_training")
  private boolean useForTraining = false;

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

  public UUID getAiRequestId() { return aiRequestId; }
  public void setAiRequestId(UUID aiRequestId) { this.aiRequestId = aiRequestId; }

  public short getRating() { return rating; }
  public void setRating(short rating) { this.rating = rating; }

  public String getComment() { return comment; }
  public void setComment(String comment) { this.comment = comment; }

  public String getCorrectedSql() { return correctedSql; }
  public void setCorrectedSql(String correctedSql) { this.correctedSql = correctedSql; }

  public boolean getUseForTraining() { return useForTraining; }
  public void setUseForTraining(boolean useForTraining) { this.useForTraining = useForTraining; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
