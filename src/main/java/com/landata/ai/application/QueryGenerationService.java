package com.landata.ai.application;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.landata.ai.infrastructure.persistence.AiMessage;
import com.landata.ai.infrastructure.persistence.AiMessageRepository;
import com.landata.ai.infrastructure.persistence.AiProviderConfig;
import com.landata.ai.infrastructure.persistence.AiProviderConfigRepository;
import com.landata.ai.infrastructure.persistence.AiRequest;
import com.landata.ai.infrastructure.persistence.AiRequestRepository;
import com.landata.datasource.application.DatasourceService;
import com.landata.datasource.infrastructure.persistence.CatalogColumn;
import com.landata.datasource.infrastructure.persistence.CatalogColumnRepository;
import com.landata.datasource.infrastructure.persistence.CatalogTable;
import com.landata.datasource.infrastructure.persistence.CatalogTableRepository;
import com.landata.datasource.infrastructure.persistence.Datasource;
import com.landata.knowledge.application.DocIndexer;
import com.landata.knowledge.application.RetrievalService;
import com.landata.knowledge.infrastructure.persistence.SchemaDoc;
import com.landata.knowledge.infrastructure.persistence.SchemaDocRepository;
import com.landata.query.application.QueryService;
import com.landata.query.application.QuestionService;
import com.landata.query.infrastructure.sql.DialectTokens;
import com.landata.query.infrastructure.sql.NativeSql;
import com.landata.query.infrastructure.sql.QueryValidator;
import com.landata.query.infrastructure.sql.SqlServerGuards;
import com.landata.shared.config.LandataProperties;
import com.landata.shared.crypto.SecretCipher;
import com.landata.shared.api.ApiException;
import com.landata.shared.security.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QueryGenerationService {
  private static final Pattern SQL_FENCE = Pattern.compile("```sql\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
  private static final Pattern JSON_FENCE = Pattern.compile("```json\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

  private final AiRequestRepository requests;
  private final AiMessageRepository messages;
  private final AiProviderConfigRepository providers;
  private final DatasourceService datasources;
  private final CatalogTableRepository tables;
  private final CatalogColumnRepository columns;
  private final SchemaDocRepository docs;
  private final SecretCipher cipher;
  private final LandataProperties properties;
  private final LlmClient llm;
  private final ModelProviderCatalog catalog;
  private final QuestionService questions;
  private final QueryService queryService;
  private final PlatformTransactionManager transactions;
  private final RetrievalService retrieval;
  private final DocIndexer indexer;
  private final PromptDocs promptDocs;
  private final ObjectMapper mapper = new ObjectMapper();
  private final JobScheduler jobs;

  public QueryGenerationService(
      AiRequestRepository requests,
      AiMessageRepository messages,
      AiProviderConfigRepository providers,
      DatasourceService datasources,
      CatalogTableRepository tables,
      CatalogColumnRepository columns,
      SchemaDocRepository docs,
      SecretCipher cipher,
      LandataProperties properties,
      LlmClient llm,
      ModelProviderCatalog catalog,
      QuestionService questions,
      QueryService queryService,
      JobScheduler jobs,
      PlatformTransactionManager transactions,
      RetrievalService retrieval,
      DocIndexer indexer,
      PromptDocs promptDocs
  ) {
    this.requests = requests;
    this.messages = messages;
    this.providers = providers;
    this.datasources = datasources;
    this.tables = tables;
    this.columns = columns;
    this.docs = docs;
    this.cipher = cipher;
    this.properties = properties;
    this.llm = llm;
    this.catalog = catalog;
    this.questions = questions;
    this.queryService = queryService;
    this.jobs = jobs;
    this.transactions = transactions;
    this.retrieval = retrieval;
    this.indexer = indexer;
    this.promptDocs = promptDocs;
  }

  public void enqueue(UUID requestId) {
    jobs.enqueue(() -> process(requestId));
  }

  @Transactional
  public void process(UUID requestId) {
    AiRequest request = requests.findById(requestId).orElse(null);
    if (request == null || !"queued".equals(request.getStatus())) return;
    request.setStatus("generating");
    requests.save(request);
    try {
      Datasource datasource = datasources.load(request.getCompanyId(), request.getDatasourceId());
      List<CatalogTable> catalog = tables.findByDatasourceIdAndCompanyId(datasource.getId(), request.getCompanyId())
          .stream().filter(CatalogTable::getVisible).toList();
      List<SchemaDoc> schemaDocs = docs.findByDatasourceIdAndCompanyIdAndCurrentTrue(datasource.getId(), request.getCompanyId());
      try {
        if (!retrieval.hasIndex(request.getCompanyId(), datasource.getId())) {
          for (SchemaDoc doc : schemaDocs) {
            try {
              indexer.index(doc, indexName(doc));
            } catch (RuntimeException ignored) {
              // Keyword fallback still works when VECTOR insert is unavailable.
            }
          }
        }
      } catch (RuntimeException ignored) {
        // Retrieval still falls back to overview + keyword docs.
      }
      List<SchemaDoc> picked = retrieval.forQuestion(request.getCompanyId(), datasource.getId(), request.getUserPrompt(), schemaDocs);

      Map<String, Object> found = new LinkedHashMap<>();
      found.put("table_count", catalog.size());
      found.put("doc_count", schemaDocs.size());
      found.put("retrieved_doc_count", picked.size());
      String chartType = promptDocs.resolveChart(requestedChart(request), request.getUserPrompt());
      found.put("chart_type", chartType);
      request.setRetrieval(found);

      String context = buildContext(datasource, catalog, picked);
      AuthPrincipal runner = new AuthPrincipal(request.getUserId(), request.getCompanyId(), "editor", null);
      String accepted = null;
      String acceptedChart = chartType;
      String summary = "SQL checked against the database.";
      String failure = null;
      String previousSql = null;
      for (int attempt = 1; attempt <= 3; attempt++) {
        String attemptContext = failure == null
            ? context
            : repairContext(context, previousSql, failure, datasource.getType());
        Draft draft = generateSql(request, datasource, catalog, schemaDocs, attemptContext, chartType);
        summary = draft.summary();
        String sql;
        try {
          sql = guard(datasource.getType(), prepareAskSql(datasource.getType(), draft.sql()), false);
        } catch (RuntimeException ex) {
          previousSql = draft.sql();
          failure = ex.getMessage() == null ? "Invalid SQL draft" : ex.getMessage();
          continue;
        }
        String error = executeDraft(runner, datasource, sql);
        if (error == null) {
          accepted = sql;
          acceptedChart = draft.chartType();
          break;
        }
        if (overflow(error)) {
          String widened = guard(datasource.getType(), sql, true);
          if (!widened.equals(sql)) {
            String again = executeDraft(runner, datasource, widened);
            if (again == null) {
              accepted = widened;
              acceptedChart = draft.chartType();
              summary = summary + " Aggregates were widened so SQL Server does not overflow int.";
              break;
            }
            error = again;
            sql = widened;
          }
        }
        previousSql = sql;
        failure = error;
      }
      if (accepted == null) {
        try {
          Draft fallback = heuristicSql(request.getUserPrompt(), datasource, catalog, schemaDocs, chartType);
          String sql = guard(datasource.getType(), prepareAskSql(datasource.getType(), fallback.sql()), false);
          String error = executeDraft(runner, datasource, sql);
          if (error == null) {
            accepted = sql;
            acceptedChart = fallback.chartType();
            summary = fallback.summary() + " A simpler query was used after the first draft failed the database check.";
          }
        } catch (RuntimeException ignored) {
          // Keep the original database error for the user.
        }
        if (accepted == null) {
          fail(request, "The query was run on the database and rejected, so no answer was returned. " + failure);
          return;
        }
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("native_sql", accepted);
      result.put("summary", summary);
      result.put("parameters", List.of());
      result.put("mode", "native");
      result.put("viz", Map.of("template", acceptedChart == null ? "table" : acceptedChart, "config", Map.of()));
      request.setResult(result);
      request.setValidation(Map.of("valid", true));
      request.setStatus("ready");
      request.setFinishedAt(Instant.now());
      requests.save(request);

      AiMessage assistant = new AiMessage();
      assistant.setCompanyId(request.getCompanyId());
      assistant.setConversationId(request.getConversationId());
      assistant.setRole("assistant");
      assistant.setContent(summary + "\nChart: " + acceptedChart + "\n```sql\n" + accepted + "\n```");
      assistant.setGenerated(result);
      messages.save(assistant);
    } catch (Exception ex) {
      request.setStatus("failed");
      request.setErrorCode("GENERATION_FAILED");
      request.setErrorMessage(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      request.setFinishedAt(Instant.now());
      requests.save(request);
      AiMessage assistant = new AiMessage();
      assistant.setCompanyId(request.getCompanyId());
      assistant.setConversationId(request.getConversationId());
      assistant.setRole("assistant");
      assistant.setContent("Failed to generate SQL: " + request.getErrorMessage());
      messages.save(assistant);
    }
  }

  @Transactional
  public Map<String, Object> accept(AuthPrincipal principal, UUID requestId, String name, String chart, String sql) {
    AiRequest request = requests.findByIdAndCompanyId(requestId, principal.companyId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "AI request not found"));
    if (request.getDatasourceId() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NO_DATASOURCE", "This draft has no data source. Ask again after picking one.");
    }
    if (request.getResult() == null || request.getResult().get("native_sql") == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NO_DRAFT", "No draft to accept");
    }
    String nativeSql = sql == null || sql.isBlank() ? String.valueOf(request.getResult().get("native_sql")) : sql.trim();
    Datasource datasource = datasources.load(principal.companyId(), request.getDatasourceId());
    String error = executeDraft(principal, datasource, nativeSql);
    if (error != null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "QUERY_FAILED", "Check the query first. The database rejected it: " + error);
    }
    Map<String, Object> result = new LinkedHashMap<>(request.getResult());
    result.put("native_sql", nativeSql);
    request.setResult(result);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("datasource_id", request.getDatasourceId());
    body.put("name", name == null || name.isBlank() ? "AI question" : name);
    body.put("mode", "native");
    body.put("native_sql", nativeSql);
    body.put("parameters", request.getResult().getOrDefault("parameters", List.of()));
    if (request.getResult().get("viz") instanceof Map<?, ?> viz) {
      Object template = ((Map<?, ?>) viz).get("template");
      if ((chart == null || chart.isBlank()) && template != null) body.put("viz_template_key", template);
      Object config = ((Map<?, ?>) viz).get("config");
      if (config instanceof Map<?, ?> cfg) body.put("viz_config", cfg);
    }
    if (chart != null && !chart.isBlank()) body.put("viz_template_key", chart);
    Map<String, Object> question = questions.create(principal, body);
    result.put("question_id", question.get("id"));
    request.setResult(result);
    request.setStatus("accepted");
    requests.save(request);
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("id", request.getId());
    view.put("status", request.getStatus());
    view.put("result", result);
    return view;
  }

  @Transactional
  public Map<String, Object> discard(UUID companyId, UUID requestId) {
    AiRequest request = requests.findByIdAndCompanyId(requestId, companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "AI request not found"));
    request.setStatus("discarded");
    request.setFinishedAt(Instant.now());
    requests.save(request);
    return Map.of("id", request.getId(), "status", "discarded");
  }

  private Draft generateSql(
      AiRequest request,
      Datasource datasource,
      List<CatalogTable> catalog,
      List<SchemaDoc> schemaDocs,
      String context,
      String chartType
  ) throws Exception {
    Optional<AiProviderConfig> config = request.getProviderConfigId() == null
        ? Optional.empty()
        : providers.findByIdAndCompanyId(request.getProviderConfigId(), request.getCompanyId());
    if (config.isEmpty()) {
      config = providers.findByCompanyIdOrderByCreatedAtAsc(request.getCompanyId()).stream()
          .filter(AiProviderConfig::getActive)
          .reduce((first, later) -> later);
    }
    String providerId = config.map(AiProviderConfig::getProvider).orElse("local");
    ModelProviderCatalog.ModelProvider provider = this.catalog.all().stream()
        .filter(item -> item.id().equalsIgnoreCase(providerId))
        .findFirst()
        .orElse(null);
    if (provider == null || "heuristic".equals(provider.format())) {
      return heuristicSql(request.getUserPrompt(), datasource, catalog, schemaDocs, chartType);
    }
    AiProviderConfig saved = config.orElseThrow();
    String key = cipher.decrypt(
        saved.getApiKeyCiphertext(),
        request.getCompanyId().toString().getBytes(StandardCharsets.UTF_8));
    String url = provider.chatUrl();
    String format = provider.format();
    String label = provider.name();
    if (saved.getSettings() != null) {
      Object apiUrl = saved.getSettings().get("api_url");
      if (apiUrl == null || String.valueOf(apiUrl).isBlank()) apiUrl = saved.getSettings().get("base_url");
      if (apiUrl != null && !String.valueOf(apiUrl).isBlank()) url = String.valueOf(apiUrl).trim();
      Object storedFormat = saved.getSettings().get("format");
      if (storedFormat != null && !String.valueOf(storedFormat).isBlank()) format = String.valueOf(storedFormat);
      Object name = saved.getSettings().get("name");
      if (name != null && !String.valueOf(name).isBlank()) label = String.valueOf(name);
    }
    String model = modelFor(saved, "query_writer", provider);
    String raw = llm.complete(
        format,
        url,
        key,
        model,
        promptDocs.systemPrompt(datasource.getType()),
        promptDocs.userMessage(
            datasource.getType(),
            chartType,
            requestedChart(request),
            request.getUserPrompt(),
            context
        )
    );
    return parseDraft(raw, chartType, "SQL from " + label + " (" + model + ") using main.md, chart.md, and schema docs.");
  }

  private String executeDraft(AuthPrincipal runner, Datasource datasource, String sql) {
    try {
      QueryValidator.assertSafeSelect(sql);
      Map<String, Object> runBody = new LinkedHashMap<>();
      runBody.put("datasource_id", datasource.getId().toString());
      runBody.put("mode", "native");
      runBody.put("native_sql", sql);
      runBody.put("parameter_definitions", List.of());
      runBody.put("parameter_values", Map.of());
      TransactionTemplate isolated = new TransactionTemplate(transactions);
      isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      isolated.executeWithoutResult(status -> queryService.run(runner, runBody, "ai_validation"));
      return null;
    } catch (RuntimeException ex) {
      String message = ex.getMessage();
      return message == null || message.isBlank() ? "Query failed" : message;
    }
  }

  private void fail(AiRequest request, String message) {
    request.setStatus("failed");
    request.setErrorCode("VALIDATION_FAILED");
    request.setErrorMessage(message);
    request.setValidation(Map.of("valid", false));
    request.setFinishedAt(Instant.now());
    requests.save(request);
    AiMessage assistant = new AiMessage();
    assistant.setCompanyId(request.getCompanyId());
    assistant.setConversationId(request.getConversationId());
    assistant.setRole("assistant");
    assistant.setContent(message);
    messages.save(assistant);
  }

  private static String guard(String dialect, String sql, boolean widen) {
    if (!"sqlserver".equals(dialect) || sql == null) return sql;
    return widen ? SqlServerGuards.widen(sql) : SqlServerGuards.countBig(sql);
  }

  private static boolean overflow(String error) {
    String text = error == null ? "" : error.toLowerCase(Locale.ROOT);
    return text.contains("overflow") || text.contains("arithmetic overflow");
  }

  private static String prepareAskSql(String dialect, String sql) {
    String expanded = DialectTokens.expand(sql, dialect);
    String ask = NativeSql.forAskDraft(expanded);
    if (DialectTokens.leftoverTokens(ask)) {
      throw new IllegalStateException("SQL still contains <<TOKEN>> placeholders. Replace them with " + dialect + " syntax.");
    }
    if (NativeSql.hasPlaceholders(ask)) {
      throw new IllegalStateException("Ask has no dashboard filters. Remove every {{parameter}} and [[optional block]].");
    }
    return ask;
  }

  private static String repairContext(String context, String sql, String error, String dialect) {
    String text = error == null ? "" : error.toLowerCase(Locale.ROOT);
    String hint;
    if (text.contains("<<") || text.contains("token")) {
      hint = "Replace every <<TOKEN>> with real " + dialect + " SQL. No << or >> may remain.";
    } else if (text.contains("{{") || text.contains("parameter") || text.contains("undeclared")) {
      hint = "Ask has no dashboard filters. Do not emit {{name}} or [[ ]]. Write a complete SELECT that can run as-is.";
    } else if (text.contains("could not be parsed") || text.contains("parse")) {
      hint = "The SQL could not be parsed. Write one valid " + dialect + " SELECT / WITH ... SELECT. Quote the date column.";
    } else if ("sqlserver".equals(dialect) && overflow(error)) {
      hint = "This is SQL Server int overflow. Use COUNT_BIG, SUM(CAST(column AS bigint)), and DATEDIFF_BIG. Do not cast to int.";
    } else {
      hint = "Change the expression so the database can execute it. For a line chart, return columns date, p1, p1_title.";
    }
    return context
        + "\n\nThe previous SELECT was executed and the database rejected it. Do not return it again.\n"
        + hint
        + "\nFailed SQL:\n"
        + (sql == null ? "" : sql)
        + "\nDatabase error:\n"
        + error
        + "\nReturn JSON {\"chart_type\",\"sql\",\"assumptions\"} or one corrected SELECT in a ```sql fence.\n";
  }

  private String modelFor(AiProviderConfig saved, String role, ModelProviderCatalog.ModelProvider provider) {
    if (saved.getSettings() != null && saved.getSettings().get("agents") instanceof Map<?, ?> agents) {
      Object chosen = agents.get(role);
      if (chosen != null && !String.valueOf(chosen).isBlank()) return String.valueOf(chosen);
    }
    if (saved.getDefaultModelId() != null && !saved.getDefaultModelId().isBlank()) return saved.getDefaultModelId();
    if (!provider.models().isEmpty()) return provider.models().get(0).id();
    throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_REQUIRED", "Choose a model for " + role);
  }

  private static String requestedChart(AiRequest request) {
    if (request.getPlan() == null || request.getPlan().get("chart_type") == null) return null;
    String value = String.valueOf(request.getPlan().get("chart_type"));
    return value.isBlank() ? null : value;
  }

  private String buildContext(Datasource datasource, List<CatalogTable> catalog, List<SchemaDoc> schemaDocs) {
    StringBuilder sb = new StringBuilder();
    sb.append("Datasource: ").append(datasource.getName()).append(" (").append(datasource.getType()).append(")\n");
    if (datasource.getDescription() != null) {
      sb.append("Description: ").append(datasource.getDescription()).append('\n');
    }
    sb.append("Tables:\n");
    for (CatalogTable table : catalog) {
      String qual = qualify(datasource.getType(), table);
      sb.append("- ").append(qual);
      List<CatalogColumn> cols = columns.findByTableIdOrderByOrdinalAsc(table.getId());
      if (!cols.isEmpty()) {
        sb.append(" (").append(cols.stream().map(CatalogColumn::getName).reduce((a, b) -> a + ", " + b).orElse("")).append(')');
      }
      sb.append('\n');
    }
    for (SchemaDoc doc : schemaDocs) {
      if (doc.getMarkdown() == null) continue;
      sb.append("\n--- ").append(doc.getKind());
      if (doc.getTitle() != null && !doc.getTitle().isBlank()) sb.append(" · ").append(doc.getTitle());
      sb.append(" ---\n");
      String md = doc.getMarkdown();
      sb.append(md.length() > 20000 ? md.substring(0, 20000) : md).append('\n');
    }
    return sb.toString();
  }

  private Draft heuristicSql(
      String prompt,
      Datasource datasource,
      List<CatalogTable> catalog,
      List<SchemaDoc> schemaDocs,
      String chartType
  ) {
    if (catalog.isEmpty()) {
      throw new IllegalStateException("No catalog tables. Sync the datasource first.");
    }
    Set<String> words = questionWords(prompt);
    CatalogTable chosen = null;
    int best = 0;
    List<CatalogColumn> chosenColumns = List.of();
    for (CatalogTable table : catalog) {
      List<CatalogColumn> cols = columns.findByTableIdOrderByOrdinalAsc(table.getId());
      int score = nameScore(table.getTableName(), words) * 3;
      for (CatalogColumn column : cols) {
        score += nameScore(column.getName(), words);
      }
      score += docScore(schemaDocs, table.getId(), words);
      if (score > best) {
        best = score;
        chosen = table;
        chosenColumns = cols;
      }
    }
    if (chosen == null) {
      throw new IllegalStateException(
          "No synced table or scraped doc matches that question. Name the table, or sync and scrape the datasource.");
    }
    boolean count = words.contains("count") || words.contains("many");
    List<CatalogColumn> matched = chosenColumns.stream().filter(column -> nameScore(column.getName(), words) > 0).toList();
    List<CatalogColumn> selected = matched.isEmpty() ? chosenColumns.stream().limit(8).toList() : matched;
    String selectList = count
        ? "COUNT(*) AS row_count"
        : selected.stream().map(column -> quote(datasource.getType(), column.getName())).reduce((a, b) -> a + ", " + b).orElse("*");
    String from = qualify(datasource.getType(), chosen);
    String sql = count
        ? "SELECT COUNT(*) AS row_count FROM " + from
        : "SELECT " + selectList + " FROM " + from;
    if (lineLike(chartType)) {
      CatalogColumn time = chosenColumns.stream().filter(QueryGenerationService::timeColumn).findFirst().orElse(null);
      if (time != null) {
        String bucket = "<<DAY(" + quote(datasource.getType(), time.getName()) + ")>>";
        String dateCol = DialectTokens.quoteIdent(datasource.getType(), "date");
        sql = "SELECT " + bucket + " AS " + dateCol
            + ", COUNT(*) AS p1, 'All' AS p1_title FROM " + from
            + " GROUP BY " + bucket
            + " ORDER BY " + dateCol;
      }
    }
    String label = (chosen.getSchemaName() == null || chosen.getSchemaName().isBlank() ? "" : chosen.getSchemaName() + ".")
        + chosen.getTableName();
    return new Draft(
        sql,
        "Using base.md and " + chosen.getTableName() + ".md (" + label + ").",
        chartType == null ? "table" : chartType
    );
  }

  private static boolean lineLike(String chartType) {
    return chartType != null && (chartType.equals("line") || chartType.equals("area")
        || chartType.equals("cumulative_line") || chartType.equals("combo"));
  }

  private static boolean timeColumn(CatalogColumn column) {
    String type = column.getNormalizedType() == null ? "" : column.getNormalizedType().toLowerCase(Locale.ROOT);
    String name = column.getName() == null ? "" : column.getName().toLowerCase(Locale.ROOT);
    return type.contains("date") || type.contains("time")
        || name.contains("date") || name.endsWith("_at") || name.contains("time");
  }

  private static final Set<String> STOP = Set.of(
      "a", "an", "the", "of", "to", "for", "and", "or", "in", "on", "me", "my", "give", "get", "show",
      "list", "data", "all", "please", "can", "you", "from", "with", "what", "which", "that", "this", "is", "are"
  );

  private Set<String> questionWords(String prompt) {
    java.util.LinkedHashSet<String> words = new java.util.LinkedHashSet<>();
    if (prompt == null) return words;
    for (String raw : prompt.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
      if (raw.length() < 3 || STOP.contains(raw)) continue;
      words.add(raw);
      if (raw.endsWith("s") && raw.length() > 4) words.add(raw.substring(0, raw.length() - 1));
    }
    return words;
  }

  private int nameScore(String identifier, Set<String> words) {
    if (identifier == null || identifier.isBlank() || words.isEmpty()) return 0;
    int score = 0;
    for (String token : identifier.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
      if (token.length() < 3) continue;
      String stem = token.endsWith("s") && token.length() > 4 ? token.substring(0, token.length() - 1) : token;
      if (words.contains(token) || words.contains(stem)) score += 4;
    }
    return score;
  }

  private int docScore(List<SchemaDoc> schemaDocs, UUID tableId, Set<String> words) {
    int score = 0;
    for (SchemaDoc doc : schemaDocs) {
      if (doc.getMarkdown() == null) continue;
      boolean aboutTable = tableId.equals(doc.getTableId()) || doc.getTableId() == null;
      if (!aboutTable) continue;
      String text = doc.getMarkdown().toLowerCase(Locale.ROOT);
      for (String word : words) {
        if (word.length() >= 4 && text.contains(word)) score++;
      }
    }
    return Math.min(score, 6);
  }

  private Draft parseDraft(String raw, String fallbackChart, String summary) throws Exception {
    JsonNode node = extractJson(raw);
    String sql = null;
    String chart = fallbackChart;
    if (node != null) {
      if (node.has("sql") && !node.get("sql").asText().isBlank()) sql = node.get("sql").asText();
      else if (node.has("native_sql") && !node.get("native_sql").asText().isBlank()) sql = node.get("native_sql").asText();
      if (node.has("chart_type") && !node.get("chart_type").asText().isBlank()) {
        String parsed = promptDocs.canonical(node.get("chart_type").asText());
        if (parsed != null) chart = parsed;
      }
      if (node.has("assumptions") && !node.get("assumptions").asText().isBlank()) {
        summary = summary + " " + node.get("assumptions").asText();
      }
    }
    if (sql == null || sql.isBlank()) {
      Matcher fence = SQL_FENCE.matcher(raw == null ? "" : raw);
      if (fence.find()) sql = fence.group(1);
    }
    if (sql == null || sql.isBlank()) {
      String trimmed = raw == null ? "" : raw.trim();
      String head = trimmed.toLowerCase(Locale.ROOT);
      if (head.startsWith("select") || head.startsWith("with")) sql = trimmed;
    }
    if (sql == null || sql.isBlank()) {
      throw new IllegalStateException("Could not parse SQL from model response");
    }
    return new Draft(sql.trim().replaceAll(";\\s*$", ""), summary, chart == null ? "table" : chart);
  }

  private JsonNode extractJson(String raw) {
    if (raw == null || raw.isBlank()) return null;
    Matcher fence = JSON_FENCE.matcher(raw);
    if (fence.find()) {
      JsonNode node = tryJson(fence.group(1));
      if (node != null) return node;
    }
    return tryJson(raw);
  }

  private JsonNode tryJson(String raw) {
    try {
      return mapper.readTree(raw);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String indexName(SchemaDoc doc) {
    if ("query_guide".equals(doc.getKind())) {
      return doc.getTitle() == null || doc.getTitle().isBlank() ? "query_guide" : doc.getTitle();
    }
    return "database_overview".equals(doc.getKind()) ? "base" : null;
  }

  private String qualify(String type, CatalogTable table) {
    if (table.getSchemaName() == null || table.getSchemaName().isBlank()) {
      return quote(type, table.getTableName());
    }
    return quote(type, table.getSchemaName()) + "." + quote(type, table.getTableName());
  }

  private record Draft(String sql, String summary, String chartType) {}

  private String quote(String type, String ident) {
    return switch (type) {
      case "mysql", "mariadb" -> "`" + ident.replace("`", "``") + "`";
      case "sqlserver" -> "[" + ident.replace("]", "]]") + "]";
      default -> "\"" + ident.replace("\"", "\"\"") + "\"";
    };
  }
}
