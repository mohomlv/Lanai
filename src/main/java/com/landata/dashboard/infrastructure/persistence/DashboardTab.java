package com.landata.dashboard.infrastructure.persistence;

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
@Table(name = "dashboard_tab")
public class DashboardTab  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "dashboard_id")
  private UUID dashboardId;

  @Column(name = "name")
  private String name;

  @Column(name = "position")
  private int position = 0;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getDashboardId() { return dashboardId; }
  public void setDashboardId(UUID dashboardId) { this.dashboardId = dashboardId; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public int getPosition() { return position; }
  public void setPosition(int position) { this.position = position; }

}
