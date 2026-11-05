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
@Table(name = "dashboard_card")
public class DashboardCard  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "dashboard_id")
  private UUID dashboardId;

  @Column(name = "tab_id")
  private UUID tabId;

  @Column(name = "card_type")
  private String cardType;

  @Column(name = "question_id")
  private UUID questionId;

  @Column(name = "text_content")
  private String textContent;

  @Column(name = "x")
  private int x;

  @Column(name = "y")
  private int y;

  @Column(name = "w")
  private int w;

  @Column(name = "h")
  private int h;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "viz_overrides")
  private Map<String, Object> vizOverrides = new LinkedHashMap<>();

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "param_mappings")
  private List<Map<String, Object>> paramMappings = new ArrayList<>();

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

  public UUID getTabId() { return tabId; }
  public void setTabId(UUID tabId) { this.tabId = tabId; }

  public String getCardType() { return cardType; }
  public void setCardType(String cardType) { this.cardType = cardType; }

  public UUID getQuestionId() { return questionId; }
  public void setQuestionId(UUID questionId) { this.questionId = questionId; }

  public String getTextContent() { return textContent; }
  public void setTextContent(String textContent) { this.textContent = textContent; }

  public int getX() { return x; }
  public void setX(int x) { this.x = x; }

  public int getY() { return y; }
  public void setY(int y) { this.y = y; }

  public int getW() { return w; }
  public void setW(int w) { this.w = w; }

  public int getH() { return h; }
  public void setH(int h) { this.h = h; }

  public Map<String, Object> getVizOverrides() { return vizOverrides; }
  public void setVizOverrides(Map<String, Object> vizOverrides) { this.vizOverrides = vizOverrides; }

  public List<Map<String, Object>> getParamMappings() { return paramMappings; }
  public void setParamMappings(List<Map<String, Object>> paramMappings) { this.paramMappings = paramMappings; }

}
