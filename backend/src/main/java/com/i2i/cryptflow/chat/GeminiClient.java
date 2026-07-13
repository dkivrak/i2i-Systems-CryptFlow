package com.i2i.cryptflow.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.i2i.cryptflow.shared.error.ApiException;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GeminiClient {
  private final WebClient client;private final String apiKey;private final String model;private final Duration timeout;
  public GeminiClient(WebClient.Builder builder,@Value("${app.gemini.api-key:}") String key,@Value("${app.gemini.model:}") String model,@Value("${app.gemini.timeout-seconds:15}") long seconds){
    client=builder.baseUrl("https://generativelanguage.googleapis.com").build();apiKey=key;this.model=model;timeout=Duration.ofSeconds(seconds);
  }
  public String generate(String prompt){
    if(apiKey.isBlank()||model.isBlank())throw unavailable("Gemini yapılandırması eksik.");
    try{
      var body=Map.of("contents",List.of(Map.of("parts",List.of(Map.of("text",prompt)))));
      JsonNode response=client.post().uri(uri->uri.path("/v1beta/models/{model}:generateContent").queryParam("key",apiKey).build(model))
          .contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve().bodyToMono(JsonNode.class).block(timeout);
      var text=response==null?null:response.at("/candidates/0/content/parts/0/text").asText(null);
      if(text==null||text.isBlank())throw unavailable("Gemini geçerli bir yanıt üretmedi.");return text;
    }catch(ApiException ex){throw ex;}catch(Exception ex){throw unavailable("Gemini servisine şu anda ulaşılamıyor.");}
  }
  private ApiException unavailable(String message){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"GEMINI_UNAVAILABLE",message);}
}

