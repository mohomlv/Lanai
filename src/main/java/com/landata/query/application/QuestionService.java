package com.landata.query.application;
import com.landata.datasource.infrastructure.persistence.Datasource;
import com.landata.query.infrastructure.persistence.CollectionFolder;
import com.landata.query.infrastructure.persistence.CollectionFolderRepository;
import com.landata.query.infrastructure.persistence.QuestionVersion;
import com.landata.query.infrastructure.persistence.QuestionVersionRepository;
import com.landata.query.infrastructure.persistence.SavedQuestion;
import com.landata.query.infrastructure.persistence.SavedQuestionRepository;
import com.landata.shared.api.ApiException;
import com.landata.shared.security.AuthPrincipal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {
  private final SavedQuestionRepository questions;
  private final QuestionVersionRepository versions;
  private final CollectionFolderRepository collections;
  private final com.landata.datasource.application.DatasourceService datasources;
  private final QueryService queryService;
  private final ParameterAccessService parameterAccess;

  public QuestionService(
      SavedQuestionRepository questions,
      QuestionVersionRepository versions,
      CollectionFolderRepository collections,
      com.landata.datasource.application.DatasourceService datasources,
      QueryService queryService,
      ParameterAccessService parameterAccess
  ) {
    this.questions = questions;
    this.versions = versions;
    this.collections = collections;
    this.datasources = datasources;
    this.queryService = queryService;
    this.parameterAccess = parameterAccess;
  }

  public List<Map<String, Object>> list(UUID companyId) {
    return questions.findByCompanyIdAndArchivedAtIsNullOrderByUpdatedAtDesc(companyId).stream().map(this::summary).toList();
  }

  @Transactional
  public Map<String, Object> create(AuthPrincipal principal, Map<String, Object> body) {
    SavedQuestion question = new SavedQuestion();
    question.setCompanyId(principal.companyId());
    question.setCreatedBy(principal.userId());
    Object datasourceId = body.get("datasource_id");
    if (datasourceId == null || String.valueOf(datasourceId).isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NO_DATASOURCE", "A data source is required");
    }
    question.setDatasourceId(UUID.fromString(String.valueOf(datasourceId)));
    datasources.load(principal.companyId(), question.getDatasourceId());
    question.setName(String.valueOf(body.get("name")));
    question.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
    if (body.get("collection_id") != null && !String.valueOf(body.get("collection_id")).isBlank()) {
      question.setCollectionId(UUID.fromString(String.valueOf(body.get("collection_id"))));
    }
    applyParameterLabel(question, body);
    questions.saveAndFlush(question);
    QuestionVersion version = version(principal, question, body, 1);
    versions.saveAndFlush(version);
    question.setCurrentVersionId(version.getId());
    questions.saveAndFlush(question);
    return detail(question, version);
  }

  @Transactional
  public Map<String, Object> get(UUID companyId, UUID id) {
    SavedQuestion question = load(companyId, id);
    return detail(question, current(question));
  }

  @Transactional
  public Map<String, Object> update(UUID companyId, UUID id, Map<String, Object> body) {
    SavedQuestion question = load(companyId, id);
    if (body.get("name") != null) question.setName(String.valueOf(body.get("name")));
    if (body.containsKey("description")) question.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
    if (body.get("collection_id") != null) question.setCollectionId(UUID.fromString(String.valueOf(body.get("collection_id"))));
    applyParameterLabel(question, body);
    question.setUpdatedAt(Instant.now());
    return summary(question);
  }

  @Transactional
  public void archive(UUID companyId, UUID id) {
    load(companyId, id).setArchivedAt(Instant.now());
  }

  @Transactional
  public Map<String, Object> addVersion(AuthPrincipal principal, UUID id, Map<String, Object> body) {
    SavedQuestion question = load(principal.companyId(), id);
    int next = versions.findByQuestionIdOrderByVersionNoDesc(id).stream().mapToInt(QuestionVersion::getVersionNo).max().orElse(0) + 1;
    QuestionVersion version = version(principal, question, body, next);
    versions.saveAndFlush(version);
    question.setCurrentVersionId(version.getId());
    question.setUpdatedAt(Instant.now());
    questions.saveAndFlush(question);
    return detail(question, version);
  }

  public List<Map<String, Object>> history(UUID companyId, UUID id) {
    load(companyId, id);
    return versions.findByQuestionIdOrderByVersionNoDesc(id).stream().map(this::versionView).toList();
  }

  public Map<String, Object> version(UUID companyId, UUID id, int number) {
    load(companyId, id);
    QuestionVersion version = versions.findByQuestionIdAndVersionNo(id, number)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Version not found"));
    return versionView(version);
  }

  @Transactional
  public Map<String, Object> run(AuthPrincipal principal, UUID id, Map<String, Object> values) {
    SavedQuestion question = load(principal.companyId(), id);
    QuestionVersion version = current(question);
    if (version == null) {
      throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Question has no saved version");
    }
    if (question.isParameterQuestion()) parameterAccess.requireSource(principal, question.getId());
    Datasource datasource = datasources.load(principal.companyId(), question.getDatasourceId());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("mode", version.getMode());
    body.put("datasource_id", datasource.getId());
    body.put("query_spec", version.getQuerySpec());
    body.put("native_sql", version.getNativeSql());
    body.put("parameter_definitions", version.getParameters());
    body.put("parameter_values", values == null ? Map.of() : values);
    QueryService.Prepared prepared = queryService.prepare(principal, datasource, body, java.time.ZoneId.of("UTC"));
    return queryService.executeSaved(principal, datasource, prepared, question.getId(), "card");
  }

  public Map<String, Object> options(AuthPrincipal principal, UUID id, String valueField, String displayField) {
    SavedQuestion source = parameterAccess.requireSource(principal, id);
    Map<String, Object> config = source.getParameterFields() == null ? Map.of() : source.getParameterFields();
    String valueName = firstText(valueField, ParameterDefinitions.text(config, "value_field"));
    String labelName = firstText(displayField, ParameterDefinitions.text(config, "display_field"));
    ParameterDefinitions.requireConfiguredField(config, valueName);
    ParameterDefinitions.requireConfiguredField(config, labelName);
    Map<String, Object> result = run(principal, id, Map.of());
    List<Map<String, Object>> columns = castColumns(result.get("columns"));
    int valueIndex = columnIndex(columns, valueName);
    int labelIndex = columnIndex(columns, labelName);
    if (valueIndex < 0 || labelIndex < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PARAM_SOURCE", "The source question result does not include the chosen fields");
    }
    List<Map<String, Object>> items = new java.util.ArrayList<>();
    java.util.Set<String> seen = new java.util.LinkedHashSet<>();
    if (result.get("rows") instanceof List<?> rows) {
      for (Object row : rows) {
        if (!(row instanceof List<?> cells) || valueIndex >= cells.size()) continue;
        Object rawValue = cells.get(valueIndex);
        if (rawValue == null) continue;
        String value = String.valueOf(rawValue);
        if (!seen.add(value) || items.size() >= 500) continue;
        Object rawLabel = labelIndex < cells.size() ? cells.get(labelIndex) : rawValue;
        items.add(Map.of("value", value, "label", rawLabel == null || String.valueOf(rawLabel).isBlank() ? value : String.valueOf(rawLabel)));
      }
    }
    return Map.of("items", items);
  }

  public List<Map<String, Object>> collections(UUID companyId) {
    return collections.findByCompanyIdAndArchivedAtIsNullOrderByNameAsc(companyId).stream().map(this::collectionView).toList();
  }

  @Transactional
  public Map<String, Object> createCollection(AuthPrincipal principal, Map<String, Object> body) {
    CollectionFolder folder = new CollectionFolder();
    folder.setCompanyId(principal.companyId());
    folder.setCreatedBy(principal.userId());
    folder.setName(String.valueOf(body.get("name")));
    folder.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
    if (body.get("parent_id") != null) folder.setParentId(UUID.fromString(String.valueOf(body.get("parent_id"))));
    collections.save(folder);
    return collectionView(folder);
  }

  @Transactional
  public Map<String, Object> updateCollection(UUID companyId, UUID id, Map<String, Object> body) {
    CollectionFolder folder = collections.findByIdAndCompanyId(id, companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Collection not found"));
    if (body.get("name") != null) folder.setName(String.valueOf(body.get("name")));
    if (body.containsKey("description")) folder.setDescription(body.get("description") == null ? null : String.valueOf(body.get("description")));
    return collectionView(folder);
  }

  @Transactional
  public void archiveCollection(UUID companyId, UUID id) {
    collections.findByIdAndCompanyId(id, companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Collection not found"))
        .setArchivedAt(Instant.now());
  }

  private void applyParameterLabel(SavedQuestion question, Map<String, Object> body) {
    if (body.containsKey("is_parameter")) {
      question.setParameterQuestion(Boolean.TRUE.equals(body.get("is_parameter")) || "true".equals(String.valueOf(body.get("is_parameter"))));
    }
    if (body.containsKey("parameter_fields")) {
      question.setParameterFields(ParameterDefinitions.fieldsConfig(body.get("parameter_fields")));
    }
    if (question.isParameterQuestion()) ParameterDefinitions.requireReady(question.getParameterFields());
  }

  private QuestionVersion version(AuthPrincipal principal, SavedQuestion question, Map<String, Object> body, int number) {
    QuestionVersion version = new QuestionVersion();
    version.setCompanyId(principal.companyId());
    version.setQuestionId(question.getId());
    version.setVersionNo(number);
    version.setCreatedBy(principal.userId());
    String mode = String.valueOf(body.getOrDefault("mode", "visual"));
    version.setMode(mode);
    if ("native".equals(mode)) version.setNativeSql(String.valueOf(body.get("native_sql")));
    else {
      @SuppressWarnings("unchecked")
      Map<String, Object> spec = (Map<String, Object>) body.get("query_spec");
      version.setQuerySpec(spec);
    }
    if (body.get("parameters") instanceof List<?> list) {
      List<Map<String, Object>> parameters = ParameterDefinitions.normalize(list);
      parameterAccess.assertSources(principal, parameters, question.getId());
      version.setParameters(parameters);
    }
    if (body.get("viz_template_key") != null) version.setVizTemplateKey(String.valueOf(body.get("viz_template_key")));
    if (body.get("viz_config") instanceof Map<?, ?> viz) {
      @SuppressWarnings("unchecked")
      Map<String, Object> config = (Map<String, Object>) viz;
      version.setVizConfig(config);
    }
    return version;
  }

  private QuestionVersion current(SavedQuestion question) {
    QuestionVersion version = question.getCurrentVersionId() == null
        ? null
        : versions.findById(question.getCurrentVersionId()).orElse(null);
    if (version == null) {
      version = versions.findByQuestionIdOrderByVersionNoDesc(question.getId()).stream().findFirst().orElse(null);
      if (version != null) {
        question.setCurrentVersionId(version.getId());
        question.setUpdatedAt(Instant.now());
        questions.save(question);
      }
    }
    return version;
  }

  private SavedQuestion load(UUID companyId, UUID id) {
    return questions.findByIdAndCompanyId(id, companyId)
        .filter(question -> question.getArchivedAt() == null)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Question not found"));
  }

  private Map<String, Object> summary(SavedQuestion question) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", question.getId());
    row.put("name", question.getName());
    row.put("datasource_id", question.getDatasourceId());
    row.put("collection_id", question.getCollectionId() == null ? "" : question.getCollectionId());
    row.put("current_version_id", question.getCurrentVersionId() == null ? "" : question.getCurrentVersionId());
    row.put("is_parameter", question.isParameterQuestion());
    row.put("parameter_fields", question.getParameterFields() == null ? Map.of() : question.getParameterFields());
    return row;
  }

  private Map<String, Object> detail(SavedQuestion question, QuestionVersion version) {
    Map<String, Object> row = new LinkedHashMap<>(summary(question));
    row.put("description", question.getDescription());
    row.put("version", version == null ? Map.of("native_sql", "", "parameters", List.of(), "mode", "native", "viz_template_key", "table") : versionView(version));
    return row;
  }

  private Map<String, Object> versionView(QuestionVersion version) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", version.getId());
    row.put("version_no", version.getVersionNo());
    row.put("mode", version.getMode());
    row.put("query_spec", version.getQuerySpec());
    row.put("native_sql", version.getNativeSql());
    row.put("parameters", version.getParameters());
    row.put("viz_template_key", version.getVizTemplateKey());
    row.put("viz_config", version.getVizConfig());
    row.put("source", version.getSource());
    return row;
  }

  private Map<String, Object> collectionView(CollectionFolder folder) {
    return Map.of("id", folder.getId(), "name", folder.getName(), "description", folder.getDescription() == null ? "" : folder.getDescription(),
        "parent_id", folder.getParentId() == null ? "" : folder.getParentId());
  }

  private static String firstText(String requested, String fallback) {
    if (requested != null && !requested.isBlank()) return requested.trim();
    return fallback;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> castColumns(Object raw) {
    if (raw instanceof List<?> list) return (List<Map<String, Object>>) list;
    return List.of();
  }

  private static int columnIndex(List<Map<String, Object>> columns, String name) {
    for (int i = 0; i < columns.size(); i++) {
      Object label = columns.get(i).get("name");
      if (label != null && name.equalsIgnoreCase(String.valueOf(label))) return i;
    }
    return -1;
  }
}
