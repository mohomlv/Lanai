package com.landata.ai.application;


import com.landata.ai.infrastructure.persistence.AiConversation;
import com.landata.ai.infrastructure.persistence.AiConversationRepository;
import com.landata.ai.infrastructure.persistence.AiMessage;
import com.landata.ai.infrastructure.persistence.AiMessageRepository;
import com.landata.ai.infrastructure.persistence.AiProviderConfig;
import com.landata.ai.infrastructure.persistence.AiProviderConfigRepository;
import com.landata.ai.infrastructure.persistence.AiRequest;
import com.landata.ai.infrastructure.persistence.AiRequestRepository;
import com.landata.datasource.application.DatasourceService;
import com.landata.shared.api.ApiException;
import com.landata.shared.config.LandataProperties;
import com.landata.shared.crypto.SecretCipher;
import com.landata.shared.security.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {
  private final AiConversationRepository conversations;
  private final AiMessageRepository messages;
  private final AiRequestRepository requests;
  private final AiProviderConfigRepository providers;
  private final DatasourceService datasources;
  private final SecretCipher cipher;
  private final LandataProperties properties;
  private final QueryGenerationService generation;
  private final ModelProviderCatalog catalog;
  private final LlmClient llm;

  public AiService(
      AiConversationRepository conversations,
      AiMessageRepository messages,
      AiRequestRepository requests,
      AiProviderConfigRepository providers,
      DatasourceService datasources,
      SecretCipher cipher,
      LandataProperties properties,
      @Lazy QueryGenerationService generation,
      ModelProviderCatalog catalog,
      LlmClient llm
  ) {
    this.conversations = conversations;
    this.messages = messages;
    this.requests = requests;
    this.providers = providers;
    this.datasources = datasources;
    this.cipher = cipher;
    this.properties = properties;
    this.generation = generation;
    this.catalog = catalog;
    this.llm = llm;
  }

  @Transactional
  public Map<String, Object> open(AuthPrincipal principal, UUID datasourceId, String targetType) {
    if (datasourceId != null) datasources.load(principal.companyId(), datasourceId);
    AiConversation conversation = new AiConversation();
    conversation.setCompanyId(principal.companyId());
    conversation.setUserId(principal.userId());
    conversation.setDatasourceId(datasourceId);
    conversation.setTargetType(targetType == null ? "question" : targetType);
    conversations.save(conversation);
    return Map.of("id", conversation.getId(), "target_type", conversation.getTargetType(), "status", conversation.getStatus());
  }

  public List<Map<String, Object>> conversations(AuthPrincipal principal) {
    return conversations.findByCompanyIdAndUserIdOrderByUpdatedAtDesc(principal.companyId(), principal.userId())
        .stream()
        .limit(30)
        .map(this::conversationSummary)
        .toList();
  }

  public Map<String, Object> conversation(UUID companyId, UUID id) {
    AiConversation conversation = load(companyId, id);
    Map<String, Object> row = conversationSummary(conversation);
    row.put("messages", messages.findByConversationIdOrderByCreatedAtAsc(id).stream().map(message -> Map.of(
        "id", message.getId(), "role", message.getRole(), "content", message.getContent()
    )).toList());
    return row;
  }

  @Transactional
  public Map<String, Object> message(AuthPrincipal principal, UUID conversationId, String content, String idempotencyKey, UUID providerConfigId, String chartType) {
    AiConversation conversation = load(principal.companyId(), conversationId);
    if (idempotencyKey != null) {
      var existing = requests.findByCompanyIdAndIdempotencyKey(principal.companyId(), idempotencyKey);
      if (existing.isPresent()) return requestView(existing.get());
    }
    AiMessage user = new AiMessage();
    user.setCompanyId(principal.companyId());
    user.setConversationId(conversationId);
    user.setRole("user");
    user.setContent(content);
    messages.save(user);
    if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
      conversation.setTitle(content.length() > 80 ? content.substring(0, 80) : content);
    }
    UUID agentId = providerConfigId != null ? providerConfigId : conversation.getProviderConfigId();
    if (agentId != null) {
      loadCompanyAgent(principal.companyId(), agentId);
      conversation.setProviderConfigId(agentId);
    }
    conversation.setUpdatedAt(Instant.now());
    conversations.save(conversation);
    AiRequest request = new AiRequest();
    request.setCompanyId(principal.companyId());
    request.setUserId(principal.userId());
    request.setConversationId(conversationId);
    request.setDatasourceId(conversation.getDatasourceId());
    request.setProviderConfigId(agentId);
    request.setTargetType(conversation.getTargetType());
    request.setUserPrompt(content);
    request.setIdempotencyKey(idempotencyKey);
    if (chartType != null && !chartType.isBlank()) {
      request.setPlan(Map.of("chart_type", chartType.trim()));
    }
    boolean configured = isConfigured(principal.companyId());
    if (!configured) {
      request.setStatus("failed");
      request.setErrorCode("AI_NOT_CONFIGURED");
      request.setErrorMessage("Add a model provider in Settings. Local Ollama needs no key.");
      request.setFinishedAt(Instant.now());
      requests.save(request);
      return requestView(request);
    }
    request.setStatus("queued");
    requests.save(request);
    UUID requestId = request.getId();
    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
        new org.springframework.transaction.support.TransactionSynchronization() {
          @Override
          public void afterCommit() {
            generation.enqueue(requestId);
          }
        });
    return requestView(request);
  }

  public Map<String, Object> request(UUID companyId, UUID id) {
    return requestView(requests.findByIdAndCompanyId(id, companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "AI request not found")));
  }

  public Map<String, Object> accept(AuthPrincipal principal, UUID id, String name, String chart, String sql) {
    return generation.accept(principal, id, name, chart, sql);
  }

  public Map<String, Object> discard(UUID companyId, UUID id) {
    return generation.discard(companyId, id);
  }

  public List<Map<String, Object>> savedAgents(UUID companyId) {
    return providers.findByCompanyIdOrderByCreatedAtAsc(companyId).stream().map(this::agentView).toList();
  }

  public List<Map<String, Object>> providerCatalog() {
    return catalog.all().stream().map(this::catalogView).toList();
  }

  @Transactional
  public Map<String, Object> saveProvider(AuthPrincipal principal, Map<String, Object> body) {
    String providerId = String.valueOf(body.getOrDefault("provider", "openai"));
    ModelProviderCatalog.ModelProvider provider = catalog.require(providerId);
    AiProviderConfig config = providers.findByCompanyIdOrderByCreatedAtAsc(principal.companyId()).stream()
        .filter(row -> provider.id().equals(row.getProvider()) && !isEndpoint(row))
        .findFirst()
        .orElseGet(AiProviderConfig::new);
    boolean existing = config.getId() != null && config.getApiKeyCiphertext() != null;

    String key = body.get("api_key") == null ? "" : String.valueOf(body.get("api_key")).trim();
    byte[] aad = principal.companyId().toString().getBytes(StandardCharsets.UTF_8);
    config.setCompanyId(principal.companyId());
    config.setProvider(provider.id());
    config.setActive(true);
    if (key.isBlank() && provider.noAuth()) {
      key = "none";
    }
    if (key.isBlank() && existing) {
      // Keep the company key already stored for this provider.
    } else if (key.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "API_KEY_REQUIRED", "This provider needs an API key");
    } else {
      config.setApiKeyCiphertext(cipher.encrypt(key, aad));
      config.setApiKeyKeyId(cipher.keyId());
    }
    if (body.get("default_model_id") != null) config.setDefaultModelId(String.valueOf(body.get("default_model_id")));
    config.setSettings(providerSettings(provider, body));
    providers.save(config);
    return providerView(config);
  }

  public Map<String, Object> provider(UUID companyId) {
    return providers.findFirstByCompanyIdAndActiveTrue(companyId)
        .map(this::providerView)
        .orElse(Map.of("has_api_key", false));
  }

  public Map<String, Object> discoverModels(AuthPrincipal principal, Map<String, Object> body) {
    ResolvedCall call = resolveCall(principal, body, false);
    if ("heuristic".equals(call.provider().format())) {
      return Map.of("items", List.of());
    }
    try {
      List<String> live = llm.listModels(call.provider().format(), call.url(), call.apiKey());
      if (!live.isEmpty()) return Map.of("items", live.stream().map(id -> Map.of("id", id, "name", id)).toList());
    } catch (Exception ignored) {
      // Static catalog models remain available when the endpoint has no model list.
    }
    return Map.of("items", call.provider().models().stream()
        .map(model -> Map.of("id", model.id(), "name", model.name()))
        .toList());
  }

  public Map<String, Object> testProvider(AuthPrincipal principal, Map<String, Object> body) {
    ResolvedCall call = resolveCall(principal, body, true);
    if ("heuristic".equals(call.provider().format())) {
      return Map.of("ok", true, "reply", "Local heuristic does not call a model");
    }
    try {
      String reply = llm.complete(
          callFormat(principal, call, body),
          call.url(),
          call.apiKey(),
          call.model(),
          "Reply with the single word ok.",
          "ping"
      );
      return Map.of("ok", true, "reply", reply.length() > 200 ? reply.substring(0, 200) : reply);
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_TEST_FAILED", ex.getMessage());
    }
  }

  private String callFormat(AuthPrincipal principal, ResolvedCall call, Map<String, Object> body) {
    if ("responses".equals(String.valueOf(body.getOrDefault("api_type", "")))) return "openai-responses";
    if (body.get("format") != null && !String.valueOf(body.get("format")).isBlank()) return String.valueOf(body.get("format"));
    AiProviderConfig saved = providers.findFirstByCompanyIdAndActiveTrue(principal.companyId()).orElse(null);
    if (saved != null && saved.getProvider().equals(call.provider().id()) && saved.getSettings() != null) {
      Object stored = saved.getSettings().get("format");
      if (stored != null && !String.valueOf(stored).isBlank()) return String.valueOf(stored);
    }
    return call.provider().format();
  }

  private Map<String, Object> conversationSummary(AiConversation conversation) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", conversation.getId());
    String title = conversation.getTitle();
    if (title == null || title.isBlank()) {
      title = messages.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
          .filter(message -> "user".equals(message.getRole()))
          .map(AiMessage::getContent)
          .findFirst()
          .orElse("New chat");
      if (title.length() > 80) title = title.substring(0, 80);
    }
    row.put("title", title);
    row.put("datasource_id", conversation.getDatasourceId());
    row.put("provider_config_id", conversation.getProviderConfigId());
    row.put("status", conversation.getStatus());
    row.put("updated_at", conversation.getUpdatedAt() == null ? conversation.getCreatedAt() : conversation.getUpdatedAt());
    return row;
  }

  private ResolvedCall resolveCall(AuthPrincipal principal, Map<String, Object> body, boolean requireModel) {
    String providerId = body.get("provider") == null ? "" : String.valueOf(body.get("provider"));
    AiProviderConfig saved = providers.findFirstByCompanyIdAndActiveTrue(principal.companyId()).orElse(null);
    if (providerId.isBlank() && saved != null) providerId = saved.getProvider();
    ModelProviderCatalog.ModelProvider provider = catalog.require(providerId.isBlank() ? "local" : providerId);
    String url = body.get("api_url") == null ? "" : String.valueOf(body.get("api_url")).trim();
    if (url.isBlank() && body.get("base_url") != null) url = String.valueOf(body.get("base_url")).trim();
    if (url.isBlank() && saved != null && saved.getSettings() != null) {
      Object stored = saved.getSettings().get("api_url");
      if (stored == null || String.valueOf(stored).isBlank()) stored = saved.getSettings().get("base_url");
      if (stored != null) url = String.valueOf(stored);
    }
    if (url.isBlank()) url = provider.chatUrl();
    String key = body.get("api_key") == null ? "" : String.valueOf(body.get("api_key")).trim();
    if ((key.isBlank() || "none".equals(key)) && saved != null && saved.getProvider().equals(provider.id())) {
      key = cipher.decrypt(saved.getApiKeyCiphertext(), principal.companyId().toString().getBytes(StandardCharsets.UTF_8));
    }
    if (key.isBlank() && provider.noAuth()) key = "none";
    if ((key.isBlank() || "none".equals(key)) && !provider.noAuth()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "API_KEY_REQUIRED", "This provider needs an API key");
    }
    String model = body.get("default_model_id") == null ? "" : String.valueOf(body.get("default_model_id"));
    if (model.isBlank() && saved != null) model = saved.getDefaultModelId() == null ? "" : saved.getDefaultModelId();
    if (model.isBlank() && !provider.models().isEmpty()) model = provider.models().get(0).id();
    if (requireModel && model.isBlank() && !"heuristic".equals(provider.format())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MODEL_REQUIRED", "Choose a model");
    }
    return new ResolvedCall(provider, url, key, model);
  }

  public List<Map<String, Object>> endpoints(UUID companyId) {
    return providers.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
        .filter(this::isEndpoint)
        .map(this::endpointView)
        .toList();
  }

  @Transactional
  public Map<String, Object> createEndpoint(AuthPrincipal principal, Map<String, Object> body) {
    String type = String.valueOf(body.getOrDefault("type", "openai-compatible"));
    if (!List.of("openai-compatible", "anthropic-compatible", "ollama").contains(type)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PROVIDER", "Custom endpoint type must be openai-compatible, anthropic-compatible, or ollama");
    }
    String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
    if (name.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED", "Endpoint name is required");
    }
    String apiUrl = sanitizeApiUrl(body.get("api_url") == null ? "" : String.valueOf(body.get("api_url")));
    String apiType = "responses".equals(String.valueOf(body.getOrDefault("api_type", "chat"))) ? "responses" : "chat";
    String providerId = "ollama".equals(type) ? "ollama-local" : type;
    ModelProviderCatalog.ModelProvider provider = catalog.require(providerId);
    String format = "openai-compatible".equals(type) && "responses".equals(apiType) ? "openai-responses" : provider.format();

    String key = body.get("api_key") == null ? "" : String.valueOf(body.get("api_key")).trim();
    if (key.isBlank() && !allowsMissingKey(apiUrl, provider.noAuth())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "API_KEY_REQUIRED", "This endpoint needs an API key");
    }
    if (key.isBlank()) key = "none";

    AiProviderConfig config = new AiProviderConfig();
    config.setCompanyId(principal.companyId());
    config.setProvider(providerId);
    config.setApiKeyCiphertext(cipher.encrypt(key, principal.companyId().toString().getBytes(StandardCharsets.UTF_8)));
    config.setApiKeyKeyId(cipher.keyId());
    if (body.get("default_model_id") != null) config.setDefaultModelId(String.valueOf(body.get("default_model_id")));
    Map<String, Object> settings = new LinkedHashMap<>();
    settings.put("kind", "endpoint");
    settings.put("name", name);
    settings.put("prefix", body.get("prefix") == null ? "" : String.valueOf(body.get("prefix")).trim());
    settings.put("api_type", apiType);
    settings.put("api_url", apiUrl);
    settings.put("base_url", apiUrl);
    settings.put("format", format);
    settings.put("type", type);
    config.setSettings(settings);
    config.setActive(true);
    providers.save(config);
    return endpointView(config);
  }

  @Transactional
  public Map<String, Object> useEndpoint(AuthPrincipal principal, UUID id) {
    AiProviderConfig chosen = loadEndpoint(principal, id);
    chosen.setActive(true);
    chosen.setUpdatedAt(Instant.now());
    providers.save(chosen);
    return endpointView(chosen);
  }

  @Transactional
  public Map<String, Object> deleteEndpoint(AuthPrincipal principal, UUID id) {
    AiProviderConfig chosen = loadEndpoint(principal, id);
    providers.delete(chosen);
    return Map.of("id", id, "deleted", true);
  }

  private boolean isEndpoint(AiProviderConfig config) {
    return config.getSettings() != null && "endpoint".equals(String.valueOf(config.getSettings().get("kind")));
  }

  private AiProviderConfig loadEndpoint(AuthPrincipal principal, UUID id) {
    AiProviderConfig config = providers.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Endpoint not found"));
    if (!principal.companyId().equals(config.getCompanyId()) || !isEndpoint(config)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Endpoint not found");
    }
    return config;
  }

  private Map<String, Object> endpointView(AiProviderConfig config) {
    Map<String, Object> settings = config.getSettings() == null ? Map.of() : config.getSettings();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", config.getId());
    row.put("name", settings.getOrDefault("name", config.getProvider()));
    row.put("prefix", settings.getOrDefault("prefix", ""));
    row.put("type", settings.getOrDefault("type", config.getProvider()));
    row.put("api_type", settings.getOrDefault("api_type", "chat"));
    row.put("api_url", settings.getOrDefault("api_url", settings.getOrDefault("base_url", "")));
    row.put("format", settings.getOrDefault("format", ""));
    row.put("default_model_id", config.getDefaultModelId() == null ? "" : config.getDefaultModelId());
    row.put("has_api_key", config.getApiKeyCiphertext() != null);
    row.put("active", config.getActive());
    return row;
  }

  private String sanitizeApiUrl(String raw) {
    String url = raw == null ? "" : raw.trim();
    while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
    if (url.endsWith("/messages")) url = url.substring(0, url.length() - "/messages".length());
    if (url.endsWith("/chat/completions")) url = url.substring(0, url.length() - "/chat/completions".length());
    if (url.endsWith("/responses")) url = url.substring(0, url.length() - "/responses".length());
    while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "API_URL_REQUIRED", "API URL must start with http:// or https://");
    }
    return url;
  }

  private boolean allowsMissingKey(String apiUrl, boolean noAuth) {
    if (noAuth) return true;
    String host = apiUrl.replaceFirst("^https?://", "").replaceFirst("/.*", "").replaceFirst(":\\d+$", "");
    return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
  }

  private Map<String, Object> providerSettings(ModelProviderCatalog.ModelProvider provider, Map<String, Object> body) {
    Map<String, Object> settings = new LinkedHashMap<>();
    String baseUrl = body.get("api_url") == null ? "" : String.valueOf(body.get("api_url")).trim();
    if (baseUrl.isBlank() && body.get("base_url") != null) baseUrl = String.valueOf(body.get("base_url")).trim();
    if (baseUrl.isBlank()) baseUrl = provider.chatUrl();
    settings.put("api_url", baseUrl);
    settings.put("base_url", baseUrl);
    if (body.get("agents") instanceof Map<?, ?> agents) {
      Map<String, String> clean = new LinkedHashMap<>();
      for (String role : List.of("query_writer", "description_writer", "dashboard_builder")) {
        Object value = agents.get(role);
        if (value != null && !String.valueOf(value).isBlank()) clean.put(role, String.valueOf(value));
      }
      settings.put("agents", clean);
    }
    return settings;
  }

  private Map<String, Object> providerView(AiProviderConfig config) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", config.getId());
    row.put("provider", config.getProvider());
    row.put("has_api_key", true);
    row.put("default_model_id", config.getDefaultModelId() == null ? "" : config.getDefaultModelId());
    row.put("settings", config.getSettings() == null ? Map.of() : config.getSettings());
    catalog.all().stream().filter(item -> item.id().equals(config.getProvider())).findFirst().ifPresent(item -> {
      row.put("provider_name", item.name());
      row.put("format", item.format());
      row.put("no_auth", item.noAuth());
      row.put("base_url_editable", item.baseUrlEditable());
    });
    return row;
  }

  private Map<String, Object> catalogView(ModelProviderCatalog.ModelProvider provider) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", provider.id());
    row.put("name", provider.name());
    row.put("format", provider.format());
    row.put("chat_url", provider.chatUrl());
    row.put("no_auth", provider.noAuth());
    row.put("base_url_editable", provider.baseUrlEditable());
    row.put("models", provider.models().stream().map(model -> Map.of("id", model.id(), "name", model.name())).toList());
    return row;
  }

  private record ResolvedCall(ModelProviderCatalog.ModelProvider provider, String url, String apiKey, String model) {}

  private boolean isConfigured(UUID companyId) {
    if (properties.getAi().getPlatformApiKey() != null && !properties.getAi().getPlatformApiKey().isBlank()) {
      return true;
    }
    return !providers.findByCompanyIdOrderByCreatedAtAsc(companyId).isEmpty();
  }

  private AiConversation load(UUID companyId, UUID id) {
    return conversations.findByIdAndCompanyId(id, companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Conversation not found"));
  }

  private AiProviderConfig loadCompanyAgent(UUID companyId, UUID id) {
    return providers.findByIdAndCompanyId(id, companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Saved agent not found for this company"));
  }

  private Map<String, Object> agentView(AiProviderConfig config) {
    Map<String, Object> settings = config.getSettings() == null ? Map.of() : config.getSettings();
    String name = String.valueOf(settings.getOrDefault("name", ""));
    if (name.isBlank()) {
      name = catalog.all().stream()
          .filter(item -> item.id().equals(config.getProvider()))
          .map(ModelProviderCatalog.ModelProvider::name)
          .findFirst()
          .orElse(config.getProvider());
    }
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", config.getId());
    row.put("name", name);
    row.put("provider", config.getProvider());
    row.put("default_model_id", config.getDefaultModelId() == null ? "" : config.getDefaultModelId());
    row.put("api_url", settings.getOrDefault("api_url", settings.getOrDefault("base_url", "")));
    row.put("kind", settings.getOrDefault("kind", "provider"));
    row.put("has_api_key", config.getApiKeyCiphertext() != null);
    row.put("active", config.getActive());
    return row;
  }

  private Map<String, Object> requestView(AiRequest request) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", request.getId());
    row.put("status", request.getStatus());
    row.put("error_code", request.getErrorCode());
    row.put("error_message", request.getErrorMessage());
    row.put("result", request.getResult());
    row.put("retrieval", request.getRetrieval());
    row.put("validation", request.getValidation());
    return row;
  }
}
