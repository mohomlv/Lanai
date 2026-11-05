package com.landata.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.landata.shared.api.ApiException;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ModelProviderCatalog {
  private final ObjectMapper mapper = new ObjectMapper();
  private List<ModelProvider> providers = List.of();

  @PostConstruct
  void load() throws Exception {
    try (InputStream in = new ClassPathResource("ai/providers.json").getInputStream()) {
      ModelProvider[] parsed = mapper.readValue(in, ModelProvider[].class);
      List<ModelProvider> all = new ArrayList<>(List.of(parsed));
      if (all.stream().noneMatch(provider -> "anthropic-compatible".equals(provider.id()))) {
        all.add(new ModelProvider(
            "anthropic-compatible",
            "Anthropic-compatible endpoint",
            "claude",
            "https://api.anthropic.com/v1",
            false,
            true,
            List.of()
        ));
      }
      all.add(new ModelProvider(
          "local",
          "Local heuristic (no model call)",
          "heuristic",
          "",
          true,
          false,
          List.of()
      ));
      providers = List.copyOf(all);
    }
  }

  public List<ModelProvider> all() {
    return providers;
  }

  public ModelProvider require(String id) {
    return providers.stream()
        .filter(provider -> provider.id().equalsIgnoreCase(id))
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PROVIDER", "Unknown model provider: " + id));
  }

  public record ModelProvider(
      String id,
      String name,
      String format,
      String chatUrl,
      boolean noAuth,
      boolean baseUrlEditable,
      List<ModelOption> models
  ) {}

  public record ModelOption(String id, String name) {}
}
