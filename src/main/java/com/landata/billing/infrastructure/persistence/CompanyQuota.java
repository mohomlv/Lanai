package com.landata.billing.infrastructure.persistence;

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
@Table(name = "company_quota")
public class CompanyQuota  {
@Id

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "ai_requests_per_month")
  private int aiRequestsPerMonth = 200;

  @Column(name = "queries_per_minute")
  private int queriesPerMinute = 120;

  @Column(name = "max_datasources")
  private int maxDatasources = 5;

  @Column(name = "max_rows_cap")
  private int maxRowsCap = 100000;

  @PrePersist
  void prePersist() {
  }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public int getAiRequestsPerMonth() { return aiRequestsPerMonth; }
  public void setAiRequestsPerMonth(int aiRequestsPerMonth) { this.aiRequestsPerMonth = aiRequestsPerMonth; }

  public int getQueriesPerMinute() { return queriesPerMinute; }
  public void setQueriesPerMinute(int queriesPerMinute) { this.queriesPerMinute = queriesPerMinute; }

  public int getMaxDatasources() { return maxDatasources; }
  public void setMaxDatasources(int maxDatasources) { this.maxDatasources = maxDatasources; }

  public int getMaxRowsCap() { return maxRowsCap; }
  public void setMaxRowsCap(int maxRowsCap) { this.maxRowsCap = maxRowsCap; }

}
