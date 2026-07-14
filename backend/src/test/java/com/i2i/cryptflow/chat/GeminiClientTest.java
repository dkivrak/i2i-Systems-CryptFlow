package com.i2i.cryptflow.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.i2i.cryptflow.shared.error.ApiException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@ExtendWith(OutputCaptureExtension.class)
class GeminiClientTest {
  private static final String SECRET = "test-secret-must-not-leak";

  @Test
  void sendsApiKeyInHeaderAndCombinesAllTextParts() {
    AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
    ExchangeFunction exchange = request -> {
      capturedRequest.set(request);
      return jsonResponse(
          HttpStatus.OK,
          """
              {
                "candidates": [{
                  "content": {"parts": [
                    {"text": "İlk bölüm"},
                    {"thoughtSignature": "ignored"},
                    {"text": "İkinci bölüm"}
                  ]}
                }]
              }
              """);
    };
    GeminiClient client = client(exchange, SECRET, "models/gemini-3.1-flash-lite");

    String answer = client.generate("Portföyümü özetle");

    ClientRequest request = capturedRequest.get();
    assertEquals("İlk bölüm\nİkinci bölüm", answer);
    assertEquals(
        "/v1beta/models/gemini-3.1-flash-lite:generateContent",
        request.url().getPath());
    assertNull(request.url().getQuery());
    assertEquals(SECRET, request.headers().getFirst("x-goog-api-key"));
  }

  @Test
  void mapsSystemInstructionAndUserMessageToSeparateRequestFields() {
    ExchangeFunction exchange = request -> jsonResponse(
        HttpStatus.OK,
        """
            {"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}
            """);
    GeminiClient client = client(exchange, SECRET, "gemini-3.1-flash-lite");

    Map<String, Object> body = client.buildRequestBody("system rules", "user question");

    assertEquals(
        Map.of("parts", List.of(Map.of("text", "system rules"))),
        body.get("systemInstruction"));
    assertEquals(
        List.of(Map.of(
            "role", "user",
            "parts", List.of(Map.of("text", "user question")))),
        body.get("contents"));
  }

  @Test
  void mapsProviderErrorWithoutLeakingApiKey(CapturedOutput output) {
    ExchangeFunction exchange = request -> jsonResponse(
        HttpStatus.NOT_FOUND,
        """
            {
              "error": {
                "code": 404,
                "status": "NOT_FOUND",
                "message": "Model is unavailable for key test-secret-must-not-leak"
              }
            }
            """);
    GeminiClient client = client(exchange, SECRET, "gemini-retired");

    ApiException exception = assertThrows(
        ApiException.class,
        () -> client.generate("Merhaba"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    assertEquals("GEMINI_UNAVAILABLE", exception.getCode());
    assertEquals("Gemini servisine şu anda ulaşılamıyor.", exception.getMessage());
    assertTrue(output.getAll().contains("httpStatus=404"));
    assertTrue(output.getAll().contains("providerStatus=NOT_FOUND"));
    assertTrue(output.getAll().contains("[REDACTED]"));
    assertFalse(output.getAll().contains(SECRET));
  }

  @Test
  void mapsEmptyCandidateToFallbackAndLogsBlockReason(CapturedOutput output) {
    ExchangeFunction exchange = request -> jsonResponse(
        HttpStatus.OK,
        """
            {
              "promptFeedback": {"blockReason": "SAFETY"},
              "candidates": []
            }
            """);
    GeminiClient client = client(exchange, SECRET, "gemini-3.1-flash-lite");

    ApiException exception = assertThrows(
        ApiException.class,
        () -> client.generate("Merhaba"));

    assertEquals("GEMINI_UNAVAILABLE", exception.getCode());
    assertEquals("Gemini geçerli bir yanıt üretmedi.", exception.getMessage());
    assertTrue(output.getAll().contains("blockReason=SAFETY"));
    assertFalse(output.getAll().contains(SECRET));
  }

  @Test
  void mapsNetworkFailureWithoutLoggingExceptionDetails(CapturedOutput output) {
    ExchangeFunction exchange = request -> Mono.error(
        new IllegalStateException("Sensitive request detail: " + SECRET));
    GeminiClient client = client(exchange, SECRET, "gemini-3.1-flash-lite");

    ApiException exception = assertThrows(
        ApiException.class,
        () -> client.generate("Merhaba"));

    assertEquals("GEMINI_UNAVAILABLE", exception.getCode());
    assertEquals("Gemini servisine şu anda ulaşılamıyor.", exception.getMessage());
    assertTrue(output.getAll().contains("type=IllegalStateException"));
    assertFalse(output.getAll().contains(SECRET));
  }

  @Test
  void rejectsMissingConfigurationBeforeMakingARequest() {
    ExchangeFunction exchange = request -> {
      throw new AssertionError("Ağ isteği yapılmamalı");
    };
    GeminiClient client = client(exchange, "", "gemini-3.1-flash-lite");

    ApiException exception = assertThrows(
        ApiException.class,
        () -> client.generate("Merhaba"));

    assertEquals("GEMINI_UNAVAILABLE", exception.getCode());
    assertEquals("Gemini yapılandırması eksik.", exception.getMessage());
  }

  private GeminiClient client(ExchangeFunction exchange, String apiKey, String model) {
    WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
    return new GeminiClient(builder, new ObjectMapper(), apiKey, model, 5);
  }

  private Mono<ClientResponse> jsonResponse(HttpStatus status, String body) {
    return Mono.just(ClientResponse.create(status)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(body)
        .build());
  }
}
