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
@Table(name = "dashboard_parameter")
public class DashboardParameter  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "dashboard_id")
  private UUID dashboardId;

  @Column(name = "slug")
  private String slug;

  @Column(name = "label")
  private String label;

  @Column(name = "type")
  private String type;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "default_value")
  private String defaultValue;

  @Column(name = "required")
  private boolean required = false;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "options_source")
  private Map<String, Object> optionsSource;

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

  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }

  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = label; }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getDefaultValue() { return defaultValue; }
  public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

  public boolean getRequired() { return required; }
  public void setRequired(boolean required) { this.required = required; }

  public Map<String, Object> getOptionsSource() { return optionsSource; }
  public void setOptionsSource(Map<String, Object> optionsSource) { this.optionsSource = optionsSource; }

  public int getPosition() { return position; }
  public void setPosition(int position) { this.position = position; }

}
