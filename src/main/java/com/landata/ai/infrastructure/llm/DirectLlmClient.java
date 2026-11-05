package com.landata.ai.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.landata.ai.application.LlmClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DirectLlmClient implements LlmClient {
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

  @Override
  public String complete(String format, String chatUrl, String apiKey, String model, String system, String user) throws Exception {
    String resolvedModel = model == null || model.isBlank() ? "default" : model;
    URI uri = chatUri(format, chatUrl, resolvedModel);
    String body = mapper.writeValueAsString(chatBody(format, resolvedModel, system, user));
    HttpResponse<String> response = send(uri, format, apiKey, body);
    JsonNode root = mapper.readTree(response.body());
    String text = extractText(format, root);
    if (text == null || text.isBlank()) {
      throw new IllegalStateException("Empty model response");
    }
    return text;
  }

  @Override
  public List<String> listModels(String format, String chatUrl, String apiKey) throws Exception {
    URI uri = modelsUri(format, chatUrl);
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET();
    applyAuth(builder, format, apiKey);
    HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 300) {
      throw new IllegalStateException("Model list HTTP " + response.statusCode());
    }
    JsonNode root = mapper.readTree(response.body());
    List<String> ids = new ArrayList<>();
    if ("ollama".equals(format)) {
      for (JsonNode node : root.path("models")) {
        String name = node.path("name").asText("");
        if (!name.isBlank()) ids.add(name);
      }
      return ids;
    }
    JsonNode items = root.has("data") ? root.path("data") : root.path("models");
    for (JsonNode node : items) {
      String id = node.path("id").asText(node.path("name").asText(""));
      if (id.startsWith("models/")) id = id.substring("models/".length());
      if (!id.isBlank()) ids.add(id);
    }
    return ids;
  }

  private HttpResponse<String> send(URI uri, String format, String apiKey, String body) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(90))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
    applyAuth(builder, format, apiKey);
    HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 300) {
      String detail = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
      if (detail.length() > 400) detail = detail.substring(0, 400);
      throw new IllegalStateException("Model HTTP " + response.statusCode() + ": " + detail);
    }
    return response;
  }

  private void applyAuth(HttpRequest.Builder builder, String format, String apiKey) {
    if (apiKey == null || apiKey.isBlank() || "none".equals(apiKey)) return;
    switch (format) {
      case "claude" -> builder.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
      case "gemini" -> builder.header("x-goog-api-key", apiKey);
      case "azure" -> builder.header("api-key", apiKey);
      case "ollama" -> { }
      case "openai-responses" -> builder.header("Authorization", "Bearer " + apiKey);
      default -> builder.header("Authorization", "Bearer " + apiKey);
    }
  }

  private Map<String, Object> chatBody(String format, String model, String system, String user) {
    return switch (format) {
      case "claude" -> Map.of(
          "model", model,
          "max_tokens", 8192,
          "system", system,
          "messages", List.of(Map.of("role", "user", "content", user))
      );
      case "gemini" -> {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", system))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))));
        body.put("generationConfig", Map.of("temperature", 0.1, "maxOutputTokens", 8192));
        yield body;
      }
      case "ollama" -> Map.of(
          "model", model,
          "stream", false,
          "messages", List.of(
              Map.of("role", "system", "content", system),
              Map.of("role", "user", "content", user)
          )
      );
      case "openai-responses" -> Map.of(
          "model", model,
          "input", system + "\n\n" + user
      );
      default -> Map.of(
          "model", model,
          "temperature", 0.1,
          "max_tokens", 8192,
          "messages", List.of(
              Map.of("role", "system", "content", system),
              Map.of("role", "user", "content", user)
          )
      );
    };
  }

  private String extractText(String format, JsonNode root) {
    return switch (format) {
      case "claude" -> root.path("content").path(0).path("text").asText("");
      case "gemini" -> root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
      case "ollama" -> {
        String content = root.path("message").path("content").asText("");
        yield content.isBlank() ? root.path("response").asText("") : content;
      }
      case "openai-responses" -> {
        String output = root.path("output_text").asText("");
        if (!output.isBlank()) yield output;
        yield root.path("output").path(0).path("content").path(0).path("text").asText("");
      }
      default -> root.path("choices").path(0).path("message").path("content").asText("");
    };
  }

  private URI chatUri(String format, String chatUrl, String model) {
    String url = requireHttp(chatUrl);
    if ("gemini".equals(format)) {
      String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
      if (!base.contains(":generateContent")) {
        base = base + "/" + model + ":generateContent";
      }
      return URI.create(base);
    }
    if ("openai".equals(format) || "azure".equals(format)) {
      return URI.create(normalizeOpenAi(url));
    }
    if ("openai-responses".equals(format)) {
      return URI.create(joinBase(url, "/responses"));
    }
    if ("claude".equals(format) && !url.contains("/messages")) {
      return URI.create(joinBase(url, "/messages"));
    }
    if ("ollama".equals(format) && !url.contains("/api/chat")) {
      String base = trimSlash(url);
      if (base.endsWith("/v1")) base = base.substring(0, base.length() - 3);
      return URI.create(trimSlash(base) + "/api/chat");
    }
    return URI.create(url);
  }

  private URI modelsUri(String format, String chatUrl) {
    String url = requireHttp(chatUrl);
    String models = switch (format) {
      case "ollama" -> {
        String base = trimSlash(url).replace("/api/chat", "");
        if (base.endsWith("/v1")) base = base.substring(0, base.length() - 3);
        yield trimSlash(base) + "/api/tags";
      }
      case "claude" -> joinBase(url, "/models");
      case "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models";
      case "openai-responses" -> joinBase(url, "/models");
      default -> {
        String chat = normalizeOpenAi(url);
        yield chat.replace("/chat/completions", "/models");
      }
    };
    return URI.create(models);
  }

  private String joinBase(String url, String path) {
    String base = trimSlash(url);
    if (base.endsWith("/messages")) base = base.substring(0, base.length() - "/messages".length());
    if (base.endsWith("/chat/completions")) base = base.substring(0, base.length() - "/chat/completions".length());
    if (base.endsWith("/responses")) base = base.substring(0, base.length() - "/responses".length());
    base = trimSlash(base);
    return base + path;
  }

  private String trimSlash(String url) {
    String trimmed = url;
    while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
    return trimmed;
  }

  private String normalizeOpenAi(String url) {
    String trimmed = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    if (trimmed.contains("/chat/completions")) return trimmed;
    if (trimmed.endsWith("/v1")) return trimmed + "/chat/completions";
    return trimmed + "/v1/chat/completions";
  }

  private String requireHttp(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("Model base URL is required");
    }
    URI uri = URI.create(url.trim());
    String scheme = uri.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new IllegalArgumentException("Model URL must be http or https");
    }
    if (uri.getHost() == null) {
      throw new IllegalArgumentException("Model URL has no host");
    }
    return url.trim();
  }
}
