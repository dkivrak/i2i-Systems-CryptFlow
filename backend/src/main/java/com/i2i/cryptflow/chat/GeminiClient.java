package com.i2i.cryptflow.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.i2i.cryptflow.shared.error.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GeminiClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeminiClient.class);
    private static final String RESPONSE_TEXT_PATH = "/candidates/0/content/parts/0/text";
    private static final String API_KEY_HEADER = "x-goog-api-key";

    private final WebClient client;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public GeminiClient(
            WebClient.Builder builder,
            @Value("${app.gemini.api-key:}") String key,
            @Value("${app.gemini.model:}") String model,
            @Value("${app.gemini.timeout-seconds:15}") long seconds) {
        
        this.client = builder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.apiKey = key;
        this.model = model;
        this.timeout = Duration.ofSeconds(seconds);
    }

    public String generate(String prompt) {
        if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            throw unavailable("Gemini configuration is missing.");
        }

        try {
            var body = Map.of("contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            ));

            JsonNode response = client.post()
                    .uri(uriBuilder -> uriBuilder.path("/v1beta/models/{model}:generateContent").build(model))
                    .header(API_KEY_HEADER, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);

            var text = response == null ? null : response.at(RESPONSE_TEXT_PATH).asText(null);
            if (text == null || text.isBlank()) {
                throw unavailable("Gemini did not generate a valid response.");
            }
            return text;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Gemini call failed", ex);
            throw unavailable("Gemini service is currently unavailable.");
        }
    }

    private ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_UNAVAILABLE", message);
    }
}
