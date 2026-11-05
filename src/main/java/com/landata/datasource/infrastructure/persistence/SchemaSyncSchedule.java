package com.landata.datasource.infrastructure.persistence;

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
@Table(name = "schema_sync_schedule")
public class SchemaSyncSchedule  {
@Id

  @Column(name = "datasource_id")
  private UUID datasourceId;

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "cron")
  private String cron = "0 3 * * *";

  @Column(name = "enabled")
  private boolean enabled = false;

  @Column(name = "last_run_at")
  private Instant lastRunAt;

  @Column(name = "next_run_at")
  private Instant nextRunAt;

  @PrePersist
  void prePersist() {
  }

  public UUID getDatasourceId() { return datasourceId; }
  public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public String getCron() { return cron; }
  public void setCron(String cron) { this.cron = cron; }

  public boolean getEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }

  public Instant getLastRunAt() { return lastRunAt; }
  public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }

  public Instant getNextRunAt() { return nextRunAt; }
  public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }

}
