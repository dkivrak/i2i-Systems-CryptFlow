package com.i2i.cryptflow.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.i2i.cryptflow.shared.error.ApiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class GeminiClient {
  private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
  private static final String BASE_URL = "https://generativelanguage.googleapis.com";
  private static final String API_KEY_HEADER = "x-goog-api-key";

  private final WebClient client;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String model;
  private final Duration timeout;

  public GeminiClient(
      WebClient.Builder builder,
      ObjectMapper objectMapper,
      @Value("${app.gemini.api-key:}") String apiKey,
      @Value("${app.gemini.model:}") String model,
      @Value("${app.gemini.timeout-seconds:30}") long timeoutSeconds) {
    this.client = builder.baseUrl(BASE_URL).build();
    this.objectMapper = objectMapper;
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = normalizeModel(model);
    this.timeout = Duration.ofSeconds(timeoutSeconds);
  }

  public String generate(String prompt) {
    return generate(null, prompt);
  }

  public String generate(String systemInstruction, String prompt) {
    if (apiKey.isBlank() || model.isBlank()) {
      throw unavailable("Gemini yapılandırması eksik.");
    }

    try {
      Map<String, Object> body = buildRequestBody(systemInstruction, prompt);

      JsonNode response = client.post()
          .uri("/v1beta/models/{model}:generateContent", model)
          .header(API_KEY_HEADER, apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body)
          .retrieve()
          .bodyToMono(JsonNode.class)
          .block(timeout);

      String text = extractText(response);
      if (text == null || text.isBlank()) {
        logEmptyResponse(response);
        throw unavailable("Gemini geçerli bir yanıt üretmedi.");
      }
      return text;
    } catch (ApiException exception) {
      throw exception;
    } catch (WebClientResponseException exception) {
      logProviderError(exception);
      throw unavailable("Gemini servisine şu anda ulaşılamıyor.");
    } catch (Exception exception) {
      // Exception messages can include request details. Log only the type and non-secret model name.
      log.warn(
          "Gemini API request failed: type={}, model={}",
          exception.getClass().getSimpleName(),
          model);
      throw unavailable("Gemini servisine şu anda ulaşılamıyor.");
    }
  }

  Map<String, Object> buildRequestBody(String systemInstruction, String prompt) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (systemInstruction != null && !systemInstruction.isBlank()) {
      body.put(
          "systemInstruction",
          Map.of("parts", List.of(Map.of("text", systemInstruction))));
    }
    body.put(
        "contents",
        List.of(Map.of(
            "role", "user",
            "parts", List.of(Map.of("text", prompt)))));
    return body;
  }

  private String extractText(JsonNode response) {
    if (response == null) {
      return null;
    }

    JsonNode parts = response.at("/candidates/0/content/parts");
    if (!parts.isArray()) {
      return null;
    }

    List<String> textParts = new ArrayList<>();
    for (JsonNode part : parts) {
      String text = part.path("text").asText("");
      if (!text.isBlank()) {
        textParts.add(text.strip());
      }
    }
    return String.join("\n", textParts);
  }

  private void logProviderError(WebClientResponseException exception) {
    String providerStatus = "UNKNOWN";
    String providerMessage = "No provider message";

    try {
      JsonNode error = objectMapper.readTree(exception.getResponseBodyAsString()).path("error");
      providerStatus = error.path("status").asText(providerStatus);
      providerMessage = error.path("message").asText(providerMessage);
    } catch (Exception ignored) {
      // The HTTP status is still useful when the provider returns a non-JSON body.
    }

    log.warn(
        "Gemini API request failed: httpStatus={}, providerStatus={}, providerMessage={}",
        exception.getStatusCode().value(),
        safeLogValue(providerStatus, 64),
        safeLogValue(providerMessage, 300));
  }

  private void logEmptyResponse(JsonNode response) {
    String blockReason = response == null
        ? "UNKNOWN"
        : response.at("/promptFeedback/blockReason").asText("NONE");
    String finishReason = response == null
        ? "UNKNOWN"
        : response.at("/candidates/0/finishReason").asText("UNKNOWN");
    log.warn(
        "Gemini API returned no text: model={}, blockReason={}, finishReason={}",
        model,
        safeLogValue(blockReason, 64),
        safeLogValue(finishReason, 64));
  }

  private String safeLogValue(String value, int maxLength) {
    String sanitized = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ");
    if (!apiKey.isBlank()) {
      sanitized = sanitized.replace(apiKey, "[REDACTED]");
    }
    return sanitized.length() <= maxLength
        ? sanitized
        : sanitized.substring(0, maxLength) + "...";
  }

  private static String normalizeModel(String model) {
    if (model == null) {
      return "";
    }
    String normalized = model.trim();
    return normalized.startsWith("models/")
        ? normalized.substring("models/".length())
        : normalized;
  }

  private ApiException unavailable(String message) {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_UNAVAILABLE", message);
  }
}
