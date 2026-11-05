package com.landata.ai.application;

import java.util.List;

/**
 * Port for chat-completion providers. Infrastructure adapters implement this
 * (OpenAI-compatible HTTP, Gemini, Anthropic, local heuristic wrappers, etc.).
 */
public interface LlmClient {
  String complete(String format, String chatUrl, String apiKey, String model, String system, String user)
      throws Exception;

  List<String> listModels(String format, String chatUrl, String apiKey) throws Exception;
}
