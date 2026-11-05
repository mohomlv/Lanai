package com.landata.dashboard.application;

import com.landata.dashboard.infrastructure.persistence.Dashboard;
import com.landata.dashboard.infrastructure.persistence.DashboardCard;
import com.landata.dashboard.infrastructure.persistence.DashboardCardRepository;
import com.landata.dashboard.infrastructure.persistence.DashboardParameter;
import com.landata.dashboard.infrastructure.persistence.DashboardParameterRepository;
import com.landata.dashboard.infrastructure.persistence.DashboardRepository;
import com.landata.dashboard.infrastructure.persistence.DashboardTab;
import com.landata.dashboard.infrastructure.persistence.DashboardTabRepository;
import com.landata.query.application.QuestionService;
import com.landata.shared.api.ApiException;
import com.landata.shared.security.AuthPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
  private final DashboardRepository dashboards;
  private final DashboardTabRepository tabs;
  private final DashboardCardRepository cards;
  private final DashboardParameterRepository parameters;
  private final QuestionService questions;

  public DashboardService(
      DashboardRepository dashboards,
      DashboardTabRepository tabs,
      DashboardCardRepository cards,
      DashboardParameterRepository parameters,
      QuestionService questions
  ) {
    this.dashboards = dashboards;
    this.tabs = tabs;
    this.cards = cards;
    this.parameters = parameters;
    this.questions = questions;
  }

  public List<Map<String, Object>> list(UUID companyId) {
    return dashboards.findByCompanyIdAndArchivedAtIsNullOrderByUpdatedAtDesc(companyId).stream()
        .map(dashboard -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("id", dashboard.getId());
          row.put("name", dashboard.getName());
          row.put("collection_id", dashboard.getCollectionId() == null ? "" : dashboard.getCollectionId());
          return row;
        })
        .toList();
  }

  @Transactional
  public Map<String, Object> create(AuthPrincipal principal, Map<String, Object> body) {
    Dashboard dashboard = new Dashboard();
    dashboard.setCompanyId(principal.companyId());
    dashboard.setCreatedBy(principal.userId());
    String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
    dashboard.setName(name.isBlank() ? "New dashboard" : name);
    if (dashboard.getSettings() == null) dashboard.setSettings(new LinkedHashMap<>());
    dashboard.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
    if (body.get("collection_id") != null && !String.valueOf(body.get("collection_id")).isBlank()) {
      dashboard.setCollectionId(UUID.fromString(String.valueOf(body.get("collection_id"))));
    }
    dashboards.save(dashboard);
    DashboardTab tab = new DashboardTab();
    tab.setCompanyId(principal.companyId());
    tab.setDashboardId(dashboard.getId());
    tab.setName("Overview");
    tabs.save(tab);
    return structure(dashboard);
  }

  public Map<String, Object> get(UUID companyId, UUID id) {
    return structure(load(companyId, id));
  }

  @Transactional
  public void archive(UUID companyId, UUID id) {
    Dashboard dashboard = load(companyId, id);
    dashboard.setArchivedAt(Instant.now());
  }

  @Transactional
  public Map<String, Object> addCard(AuthPrincipal principal, UUID id, Map<String, Object> body) {
    Dashboard dashboard = load(principal.companyId(), id);
    DashboardCard card = new DashboardCard();
    card.setCompanyId(principal.companyId());
    card.setDashboardId(dashboard.getId());
    card.setCardType(String.valueOf(body.getOrDefault("card_type", "question")));
    if (body.get("question_id") != null) card.setQuestionId(UUID.fromString(String.valueOf(body.get("question_id"))));
    if (body.get("tab_id") != null) card.setTabId(UUID.fromString(String.valueOf(body.get("tab_id"))));
    card.setTextContent(body.get("text_content") == null ? null : String.valueOf(body.get("text_content")));
    card.setX(number(body.get("x"), 0));
    card.setY(number(body.get("y"), 0));
    card.setW(number(body.get("w"), 6));
    card.setH(number(body.get("h"), 4));
    if (body.get("param_mappings") instanceof List<?> mappings) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> typed = (List<Map<String, Object>>) mappings;
      card.setParamMappings(typed);
    }
    cards.save(card);
    return cardView(card);
  }

  @Transactional
  public Map<String, Object> updateCard(UUID companyId, UUID cardId, Map<String, Object> body) {
    DashboardCard card = cards.findByIdAndCompanyId(cardId, companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Card not found"));
    if (body.get("x") != null) card.setX(number(body.get("x"), card.getX()));
    if (body.get("y") != null) card.setY(number(body.get("y"), card.getY()));
    if (body.get("w") != null) card.setW(number(body.get("w"), card.getW()));
    if (body.get("h") != null) card.setH(number(body.get("h"), card.getH()));
    if (body.get("param_mappings") instanceof List<?> mappings) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> typed = (List<Map<String, Object>>) mappings;
      card.setParamMappings(typed);
    }
    return cardView(card);
  }

  @Transactional
  public void deleteCard(UUID companyId, UUID cardId) {
    DashboardCard card = cards.findByIdAndCompanyId(cardId, companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Card not found"));
    cards.delete(card);
  }

  @Transactional
  public List<Map<String, Object>> replaceParameters(UUID companyId, UUID id, List<Map<String, Object>> rows) {
    Dashboard dashboard = load(companyId, id);
    parameters.deleteByDashboardId(dashboard.getId());
    List<Map<String, Object>> saved = new ArrayList<>();
    int position = 0;
    for (Map<String, Object> row : rows) {
      DashboardParameter parameter = new DashboardParameter();
      parameter.setCompanyId(companyId);
      parameter.setDashboardId(dashboard.getId());
      parameter.setSlug(String.valueOf(row.get("slug")));
      parameter.setLabel(String.valueOf(row.getOrDefault("label", row.get("slug"))));
      parameter.setType(String.valueOf(row.get("type")));
      parameter.setRequired(Boolean.TRUE.equals(row.get("required")));
      parameter.setPosition(position++);
      if (row.get("default_value") != null) parameter.setDefaultValue(String.valueOf(row.get("default_value")));
      if (row.get("options_source") instanceof Map<?, ?> options) {
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) options;
        parameter.setOptionsSource(typed);
      }
      parameters.save(parameter);
      saved.add(parameterView(parameter));
    }
    return saved;
  }

  public Map<String, Object> data(AuthPrincipal principal, UUID id, Map<String, Object> body) {
    Dashboard dashboard = load(principal.companyId(), id);
    @SuppressWarnings("unchecked")
    Map<String, Object> dashboardValues = body.get("parameters") instanceof Map<?, ?> map
        ? (Map<String, Object>) map : Map.of();
    List<Map<String, Object>> results = new ArrayList<>();
    for (DashboardCard card : cards.findByDashboardId(dashboard.getId())) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("card_id", card.getId());
      if (!"question".equals(card.getCardType())) {
        item.put("text", card.getTextContent());
        results.add(item);
        continue;
      }
      try {
        Map<String, Object> values = new LinkedHashMap<>();
        if (card.getParamMappings() == null || card.getParamMappings().isEmpty()) {
          values.putAll(dashboardValues);
        }
        for (Map<String, Object> mapping : card.getParamMappings() == null ? List.<Map<String, Object>>of() : card.getParamMappings()) {
          String dashboardParam = String.valueOf(mapping.get("dashboard_param"));
          String questionParam = String.valueOf(mapping.get("question_param"));
          if (dashboardValues.containsKey(dashboardParam)) values.put(questionParam, dashboardValues.get(dashboardParam));
        }
        item.put("data", questions.run(principal, card.getQuestionId(), values));
      } catch (RuntimeException ex) {
        item.put("error", ex.getMessage());
      }
      results.add(item);
    }
    return Map.of("cards", results);
  }

  private Dashboard load(UUID companyId, UUID id) {
    return dashboards.findByIdAndCompanyId(id, companyId)
        .filter(dashboard -> dashboard.getArchivedAt() == null)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Dashboard not found"));
  }

  private Map<String, Object> structure(Dashboard dashboard) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", dashboard.getId());
    row.put("name", dashboard.getName());
    row.put("collection_id", dashboard.getCollectionId() == null ? "" : dashboard.getCollectionId());
    row.put("description", dashboard.getDescription());
    row.put("auto_refresh_seconds", dashboard.getAutoRefreshSeconds());
    row.put("tabs", tabs.findByDashboardIdOrderByPositionAsc(dashboard.getId()).stream()
        .map(tab -> Map.of("id", tab.getId(), "name", tab.getName(), "position", tab.getPosition())).toList());
    row.put("cards", cards.findByDashboardId(dashboard.getId()).stream().map(this::cardView).toList());
    row.put("parameters", parameters.findByDashboardIdOrderByPositionAsc(dashboard.getId()).stream().map(this::parameterView).toList());
    return row;
  }

  private Map<String, Object> cardView(DashboardCard card) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", card.getId());
    row.put("card_type", card.getCardType());
    row.put("question_id", card.getQuestionId());
    row.put("tab_id", card.getTabId());
    row.put("text_content", card.getTextContent());
    row.put("x", card.getX());
    row.put("y", card.getY());
    row.put("w", card.getW());
    row.put("h", card.getH());
    row.put("param_mappings", card.getParamMappings());
    return row;
  }

  private Map<String, Object> parameterView(DashboardParameter parameter) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", parameter.getId());
    row.put("slug", parameter.getSlug());
    row.put("label", parameter.getLabel());
    row.put("type", parameter.getType());
    row.put("required", parameter.getRequired());
    row.put("default_value", parameter.getDefaultValue());
    row.put("options_source", parameter.getOptionsSource());
    return row;
  }

  private static int number(Object value, int fallback) {
    return value instanceof Number number ? number.intValue() : fallback;
  }
}
