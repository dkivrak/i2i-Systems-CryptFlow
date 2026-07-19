package com.i2i.cryptflow.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info = @Info(
        title = "CryptFlow API",
        version = "0.0.1-SNAPSHOT",
        description = "REST API for CryptFlow paper trading, portfolios, market data, alerts, orders, news, and AI assistance."
    )
)
@SecurityScheme(
    name = OpenApiConfig.BEARER_AUTH,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "UUID",
    description = "Opaque UUID session token returned by POST /api/auth/login and stored in Redis. Enter the token only; Swagger UI adds the Bearer prefix."
)
public class OpenApiConfig {
  public static final String BEARER_AUTH = "bearerAuth";

  @Bean
  OpenApiCustomizer cryptFlowOpenApiCustomizer() {
    return openApi -> {
      markStrictlyPositive(openApi, "TradeRequest", "quantity");
      markStrictlyPositive(openApi, "PlaceOrderRequest", "targetPrice");
      markStrictlyPositive(openApi, "PlaceOrderRequest", "quantity");
      markStrictlyPositive(openApi, "CreateAlertRequest", "targetPrice");
      markMinimumLength(openApi, "ChatRequest", "message", 1);

      openApi.getPaths().values().forEach(pathItem ->
          pathItem.readOperations().forEach(operation -> {
            if (operation.getRequestBody() != null) {
              useJsonMediaType(operation.getRequestBody().getContent());
            }
            if (operation.getResponses() != null) {
              operation.getResponses().values().forEach(response ->
                  useJsonMediaType(response.getContent()));
            }
          })
      );
    };
  }

  private static void markStrictlyPositive(OpenAPI openApi, String schemaName, String propertyName) {
    Schema<?> property = property(openApi, schemaName, propertyName);
    if (property != null) {
      property.setMinimum(null);
      property.setExclusiveMinimumValue(BigDecimal.ZERO);
    }
  }

  private static void markMinimumLength(
      OpenAPI openApi,
      String schemaName,
      String propertyName,
      int minimumLength
  ) {
    Schema<?> property = property(openApi, schemaName, propertyName);
    if (property != null) {
      property.setMinLength(minimumLength);
    }
  }

  private static Schema<?> property(OpenAPI openApi, String schemaName, String propertyName) {
    if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
      return null;
    }
    Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
    if (schema == null || schema.getProperties() == null) {
      return null;
    }
    return schema.getProperties().get(propertyName);
  }

  private static void useJsonMediaType(Content content) {
    if (content == null || !content.containsKey(MediaType.ALL_VALUE)) {
      return;
    }
    var mediaType = content.remove(MediaType.ALL_VALUE);
    content.putIfAbsent(MediaType.APPLICATION_JSON_VALUE, mediaType);
  }
}
