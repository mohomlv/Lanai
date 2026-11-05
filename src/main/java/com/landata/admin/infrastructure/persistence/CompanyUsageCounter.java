package com.landata.admin.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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
@Table(name = "company_usage_counter")
@IdClass(CompanyUsageKey.class)
public class CompanyUsageCounter  {
@Id

  @Column(name = "company_id")
  private UUID companyId;

@Id

  @Column(name = "period")
  private LocalDate period;

  @Column(name = "ai_requests")
  private int aiRequests = 0;

  @Column(name = "ai_tokens")
  private long aiTokens = 0;

  @Column(name = "queries")
  private long queries = 0;

  @PrePersist
  void prePersist() {
  }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public LocalDate getPeriod() { return period; }
  public void setPeriod(LocalDate period) { this.period = period; }

  public int getAiRequests() { return aiRequests; }
  public void setAiRequests(int aiRequests) { this.aiRequests = aiRequests; }

  public long getAiTokens() { return aiTokens; }
  public void setAiTokens(long aiTokens) { this.aiTokens = aiTokens; }

  public long getQueries() { return queries; }
  public void setQueries(long queries) { this.queries = queries; }

}
