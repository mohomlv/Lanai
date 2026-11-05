package com.landata.query.application;

import com.landata.query.infrastructure.persistence.RoleParameterQuestion;
import com.landata.query.infrastructure.persistence.RoleParameterQuestionRepository;
import com.landata.query.infrastructure.persistence.SavedQuestion;
import com.landata.query.infrastructure.persistence.SavedQuestionRepository;
import com.landata.shared.api.ApiException;
import com.landata.shared.security.AuthPrincipal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParameterAccessService {
  private final SavedQuestionRepository questions;
  private final RoleParameterQuestionRepository grants;

  public ParameterAccessService(SavedQuestionRepository questions, RoleParameterQuestionRepository grants) {
    this.questions = questions;
    this.grants = grants;
  }

  public List<Map<String, Object>> listAllowed(AuthPrincipal principal) {
    Set<UUID> allowed = grantedIds(principal.companyId(), principal.role());
    if (allowed.isEmpty()) return List.of();
    return parameterQuestions(principal.companyId()).stream()
        .filter(question -> allowed.contains(question.getId()))
        .map(this::brief)
        .toList();
  }

  public Map<String, Object> read(AuthPrincipal principal) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("questions", parameterQuestions(principal.companyId()).stream().map(this::brief).toList());
    body.put("grants", grants.findByCompanyId(principal.companyId()).stream().map(this::grantView).toList());
    return body;
  }

  @Transactional
  public Map<String, Object> replace(AuthPrincipal principal, Map<String, Object> body) {
    Object raw = body.get("grants");
    if (!(raw instanceof List<?> list)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PARAM_SOURCE", "grants must be a list");
    }
    if (list.size() > 500) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PARAM_SOURCE", "Too many parameter grants");
    }
    Set<UUID> parameterIds = new LinkedHashSet<>();
    for (SavedQuestion question : parameterQuestions(principal.companyId())) parameterIds.add(question.getId());
    grants.deleteByCompanyId(principal.companyId());
    Set<String> seen = new LinkedHashSet<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PARAM_SOURCE", "A grant must be an object");
      }
      String role = ParameterDefinitions.text(copy(map), "role");
      if (role == null || !ParameterDefinitions.ROLES.contains(role)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PARAM_SOURCE", "Role is not a company role");
      }
      UUID questionId = parseId(map.get("question_id"));
      if (!parameterIds.contains(questionId)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PARAM_SOURCE", "Only parameter questions can be granted");
      }
      String key = role + ":" + questionId;
      if (!seen.add(key)) continue;
      RoleParameterQuestion grant = new RoleParameterQuestion();
      grant.setCompanyId(principal.companyId());
      grant.setRole(role);
      grant.setQuestionId(questionId);
      grants.save(grant);
    }
    return read(principal);
  }

  public void assertSources(AuthPrincipal principal, List<Map<String, Object>> parameters, UUID selfId) {
    if (parameters == null) return;
    for (Map<String, Object> parameter : parameters) {
      if (!(parameter.get("options_source") instanceof Map<?, ?> source)) continue;
      if (!"query".equals(String.valueOf(source.get("kind")))) continue;
      UUID questionId = parseId(source.get("question_id"));
      if (selfId != null && selfId.equals(questionId)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PARAM_SOURCE", "A question cannot use itself as a parameter source");
      }
      SavedQuestion sourceQuestion = requireSource(principal, questionId);
      Map<String, Object> fields = sourceQuestion.getParameterFields();
      ParameterDefinitions.requireConfiguredField(fields, ParameterDefinitions.text(copy(source), "value_field"));
      ParameterDefinitions.requireConfiguredField(fields, ParameterDefinitions.text(copy(source), "display_field"));
      String sourceField = ParameterDefinitions.text(copy(source), "source_field");
      if (sourceField != null) ParameterDefinitions.requireConfiguredField(fields, sourceField);
    }
  }

  public SavedQuestion requireSource(AuthPrincipal principal, UUID questionId) {
    SavedQuestion question = questions.findByIdAndCompanyId(questionId, principal.companyId())
        .filter(item -> item.getArchivedAt() == null && item.isParameterQuestion())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Parameter question is not available"));
    if (principal.role() == null || !grants.existsByCompanyIdAndRoleAndQuestionId(
        principal.companyId(), principal.role(), questionId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_PARAM_SOURCE", "Parameter question is not available for your role");
    }
    return question;
  }

  private Set<UUID> grantedIds(UUID companyId, String role) {
    if (role == null) return Set.of();
    Set<UUID> ids = new LinkedHashSet<>();
    for (RoleParameterQuestion grant : grants.findByCompanyIdAndRole(companyId, role)) ids.add(grant.getQuestionId());
    return ids;
  }

  private List<SavedQuestion> parameterQuestions(UUID companyId) {
    return questions.findByCompanyIdAndParameterQuestionTrueAndArchivedAtIsNullOrderByNameAsc(companyId);
  }

  private Map<String, Object> brief(SavedQuestion question) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", question.getId());
    row.put("name", question.getName());
    row.put("parameter_fields", question.getParameterFields() == null ? Map.of() : question.getParameterFields());
    return row;
  }

  private Map<String, Object> grantView(RoleParameterQuestion grant) {
    return Map.of("role", grant.getRole(), "question_id", grant.getQuestionId());
  }

  private static UUID parseId(Object raw) {
    try {
      return UUID.fromString(String.valueOf(raw));
    } catch (IllegalArgumentException ex) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PARAM_SOURCE", "Question id is invalid");
    }
  }

  private static Map<String, Object> copy(Map<?, ?> raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    raw.forEach((key, value) -> out.put(String.valueOf(key), value));
    return out;
  }
}
