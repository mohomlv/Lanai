package com.landata.ai.application;

import com.landata.ai.infrastructure.persistence.AiProviderConfig;
import com.landata.ai.infrastructure.persistence.AiProviderConfigRepository;
import com.landata.shared.api.ApiException;
import com.landata.shared.crypto.SecretCipher;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Completes one prompt with the company's saved model. Empty when no model is configured. */
@Service
public class CompanyModel {
  private final AiProviderConfigRepository providers;
  private final ModelProviderCatalog catalog;
  private final SecretCipher cipher;
  private final LlmClient llm;

  public CompanyModel(
      AiProviderConfigRepository providers,
      ModelProviderCatalog catalog,
      SecretCipher cipher,
      LlmClient llm
  ) {
    this.providers = providers;
    this.catalog = catalog;
    this.cipher = cipher;
    this.llm = llm;
  }

  public Optional<String> complete(UUID companyId, String role, String system, String user) {
    try {
      Optional<AiProviderConfig> config = providers.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
          .filter(AiProviderConfig::getActive)
          .reduce((first, later) -> later);
      if (config.isEmpty()) return Optional.empty();
      AiProviderConfig saved = config.get();
      ModelProviderCatalog.ModelProvider provider = catalog.all().stream()
          .filter(item -> item.id().equalsIgnoreCase(saved.getProvider()))
          .findFirst()
          .orElse(null);
      if (provider == null || "heuristic".equals(provider.format())) return Optional.empty();
      if (saved.getApiKeyCiphertext() == null) return Optional.empty();
      String key = cipher.decrypt(saved.getApiKeyCiphertext(), companyId.toString().getBytes(StandardCharsets.UTF_8));
      String url = provider.chatUrl();
      String format = provider.format();
      if (saved.getSettings() != null) {
        Object apiUrl = saved.getSettings().get("api_url");
        if (apiUrl == null || String.valueOf(apiUrl).isBlank()) apiUrl = saved.getSettings().get("base_url");
        if (apiUrl != null && !String.valueOf(apiUrl).isBlank()) url = String.valueOf(apiUrl).trim();
        Object storedFormat = saved.getSettings().get("format");
        if (storedFormat != null && !String.valueOf(storedFormat).isBlank()) format = String.valueOf(storedFormat);
      }
      String text = llm.complete(format, url, key, modelFor(saved, role, provider), system, user);
      if (text == null || text.isBlank()) return Optional.empty();
      return Optional.of(unwrap(text));
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  private String modelFor(AiProviderConfig saved, String role, ModelProviderCatalog.ModelProvider provider) {
    if (saved.getSettings() != null && saved.getSettings().get("agents") instanceof Map<?, ?> agents) {
      Object chosen = agents.get(role);
      if (chosen != null && !String.valueOf(chosen).isBlank()) return String.valueOf(chosen);
    }
    if (saved.getDefaultModelId() != null && !saved.getDefaultModelId().isBlank()) return saved.getDefaultModelId();
    if (!provider.models().isEmpty()) return provider.models().get(0).id();
    throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "MODEL_REQUIRED", "Choose a model for " + role);
  }

  private static String unwrap(String raw) {
    String text = raw.trim();
    if (text.startsWith("```")) {
      int newline = text.indexOf('\n');
      if (newline > 0) text = text.substring(newline + 1);
      if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
    }
    return text.trim();
  }
}
