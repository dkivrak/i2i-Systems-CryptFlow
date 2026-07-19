package com.i2i.cryptflow.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.i2i.cryptflow.auth.AuthService;
import com.i2i.cryptflow.auth.SessionService;
import com.i2i.cryptflow.chat.ChatService;
import com.i2i.cryptflow.market.AlertService;
import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.market.NewsService;
import com.i2i.cryptflow.portfolio.PortfolioService;
import com.i2i.cryptflow.shared.config.OpenApiConfig;
import com.i2i.cryptflow.trade.OrderService;
import com.i2i.cryptflow.trade.TradeService;
import com.i2i.cryptflow.user.AccountService;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(classes = OpenApiCoverageTest.TestApplication.class, properties = "debug=false")
@AutoConfigureMockMvc(addFilters = false)
class OpenApiCoverageTest {
  private static final Set<Endpoint> PUBLIC_ENDPOINTS = Set.of(
      new Endpoint("POST", "/api/auth/register"),
      new Endpoint("POST", "/api/auth/login"),
      new Endpoint("GET", "/api/market/prices"),
      new Endpoint("GET", "/api/news")
  );
  private static final Set<String> HTTP_OPERATION_KEYS = Set.of(
      "get", "post", "put", "patch", "delete", "options", "head", "trace"
  );
  private static final Set<String> DOCUMENTED_ERROR_CODES = Set.of(
      "400", "401", "403", "404", "409", "422", "503"
  );

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RequestMappingHandlerMapping handlerMapping;

  @MockitoBean private AuthService authService;
  @MockitoBean private SessionService sessionService;
  @MockitoBean private AccountService accountService;
  @MockitoBean private PortfolioService portfolioService;
  @MockitoBean private MarketPriceService marketPriceService;
  @MockitoBean private NewsService newsService;
  @MockitoBean private AlertService alertService;
  @MockitoBean private ChatService chatService;
  @MockitoBean private TradeService tradeService;
  @MockitoBean private OrderService orderService;

  private JsonNode document;
  private Set<Endpoint> controllerEndpoints;

  @BeforeEach
  void loadGeneratedDocumentAndMappings() throws Exception {
    String body = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs")
        )
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
    document = objectMapper.readTree(body);
    controllerEndpoints = discoverControllerEndpoints();
  }

  @Test
  void generatedDocumentContainsEveryRestControllerMappingAndNoStaleOperation() {
    Set<Endpoint> documentedEndpoints = discoverDocumentedEndpoints(document);

    assertThat(documentedEndpoints)
        .as("generated OpenAPI operations must exactly match @RestController mappings")
        .containsExactlyInAnyOrderElementsOf(controllerEndpoints);
  }

  @Test
  void definesOpaqueUuidBearerSchemeAndMarksOnlyProtectedOperations() {
    JsonNode scheme = document.path("components").path("securitySchemes").path(OpenApiConfig.BEARER_AUTH);
    assertThat(scheme.path("type").asText()).isEqualTo("http");
    assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
    assertThat(scheme.path("bearerFormat").asText()).isEqualTo("UUID");

    assertThat(controllerEndpoints).containsAll(PUBLIC_ENDPOINTS);
    for (Endpoint endpoint : controllerEndpoints) {
      JsonNode operation = operation(endpoint);
      boolean bearerRequired = hasBearerRequirement(operation);
      if (PUBLIC_ENDPOINTS.contains(endpoint)) {
        assertThat(bearerRequired)
            .as("%s must remain public", endpoint)
            .isFalse();
      } else {
        assertThat(bearerRequired)
            .as("%s must require bearerAuth", endpoint)
            .isTrue();
        assertThat(operation.path("responses").has("401"))
            .as("%s must document its 401 response", endpoint)
            .isTrue();
      }
    }
  }

  @Test
  void everyOperationHasSummaryTagSuccessResponseAndStructuredDocumentedErrors() {
    for (Endpoint endpoint : controllerEndpoints) {
      JsonNode operation = operation(endpoint);
      assertThat(operation.path("summary").asText()).as("summary for %s", endpoint).isNotBlank();
      assertThat(operation.path("tags").isArray() && !operation.path("tags").isEmpty())
          .as("tag for %s", endpoint)
          .isTrue();
      assertThat(hasSuccessResponse(operation.path("responses")))
          .as("2xx response for %s", endpoint)
          .isTrue();

      Iterator<Map.Entry<String, JsonNode>> responses = operation.path("responses").properties().iterator();
      while (responses.hasNext()) {
        Map.Entry<String, JsonNode> response = responses.next();
        if (DOCUMENTED_ERROR_CODES.contains(response.getKey())) {
          assertThat(firstResponseSchemaReference(response.getValue()))
              .as("ApiError schema for %s response %s", endpoint, response.getKey())
              .isEqualTo("#/components/schemas/ApiError");
        }
      }
    }
  }

  @Test
  void everyJsonPayloadHasASchemaAndStrictlyPositiveFieldsExcludeZero() {
    for (Endpoint endpoint : controllerEndpoints) {
      JsonNode operation = operation(endpoint);
      if (operation.has("requestBody")) {
        assertJsonSchema(operation.path("requestBody").path("content"), endpoint + " request body");
      }

      Iterator<Map.Entry<String, JsonNode>> responses = operation.path("responses").properties().iterator();
      while (responses.hasNext()) {
        Map.Entry<String, JsonNode> response = responses.next();
        if (response.getKey().matches("2\\d\\d") && response.getValue().has("content")) {
          assertJsonSchema(response.getValue().path("content"), endpoint + " response " + response.getKey());
        }
        if (response.getValue().has("content")) {
          assertThat(response.getValue().path("content").has("*/*"))
              .as("wildcard media type for %s response %s", endpoint, response.getKey())
              .isFalse();
        }
      }
    }

    assertStrictlyPositive("TradeRequest", "quantity");
    assertStrictlyPositive("PlaceOrderRequest", "targetPrice");
    assertStrictlyPositive("PlaceOrderRequest", "quantity");
    assertStrictlyPositive("CreateAlertRequest", "targetPrice");
    assertThat(document.path("components").path("schemas").path("ChatRequest")
        .path("properties").path("message").path("minLength").asInt())
        .as("ChatRequest.message must document its nonblank lower bound")
        .isEqualTo(1);
  }

  private Set<Endpoint> discoverControllerEndpoints() {
    Set<Endpoint> endpoints = new HashSet<>();
    handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
      if (!handler.getBeanType().isAnnotationPresent(RestController.class)
          || !handler.getBeanType().getPackageName().startsWith("com.i2i.cryptflow")) {
        return;
      }
      paths(mapping).forEach(path -> {
        for (RequestMethod method : mapping.getMethodsCondition().getMethods()) {
          endpoints.add(new Endpoint(method.name(), path));
        }
      });
    });
    return endpoints;
  }

  private Stream<String> paths(RequestMappingInfo mapping) {
    if (mapping.getPathPatternsCondition() == null) {
      return Stream.empty();
    }
    return mapping.getPathPatternsCondition().getPatternValues().stream();
  }

  private Set<Endpoint> discoverDocumentedEndpoints(JsonNode openApi) {
    Set<Endpoint> endpoints = new HashSet<>();
    openApi.path("paths").properties().forEach(pathEntry ->
        pathEntry.getValue().properties().forEach(operationEntry -> {
          if (HTTP_OPERATION_KEYS.contains(operationEntry.getKey())) {
            endpoints.add(new Endpoint(
                operationEntry.getKey().toUpperCase(Locale.ROOT),
                pathEntry.getKey()
            ));
          }
        })
    );
    return endpoints;
  }

  private JsonNode operation(Endpoint endpoint) {
    return document.path("paths").path(endpoint.path()).path(endpoint.method().toLowerCase(Locale.ROOT));
  }

  private boolean hasBearerRequirement(JsonNode operation) {
    if (!operation.path("security").isArray()) {
      return false;
    }
    for (JsonNode requirement : operation.path("security")) {
      if (requirement.has(OpenApiConfig.BEARER_AUTH)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasSuccessResponse(JsonNode responses) {
    Iterator<String> names = responses.fieldNames();
    while (names.hasNext()) {
      if (names.next().matches("2\\d\\d")) {
        return true;
      }
    }
    return false;
  }

  private String firstResponseSchemaReference(JsonNode response) {
    Iterator<JsonNode> mediaTypes = response.path("content").elements();
    if (!mediaTypes.hasNext()) {
      return "";
    }
    return mediaTypes.next().path("schema").path("$ref").asText();
  }

  private void assertJsonSchema(JsonNode content, String label) {
    assertThat(content.has("application/json")).as("JSON media type for %s", label).isTrue();
    JsonNode schema = content.path("application/json").path("schema");
    assertThat(!schema.isMissingNode() && !schema.isEmpty())
        .as("response/request schema for %s", label)
        .isTrue();
  }

  private void assertStrictlyPositive(String schemaName, String propertyName) {
    JsonNode property = document.path("components").path("schemas").path(schemaName)
        .path("properties").path(propertyName);
    assertThat(property.has("minimum"))
        .as("%s.%s must not describe zero as valid", schemaName, propertyName)
        .isFalse();
    assertThat(property.path("exclusiveMinimum").decimalValue())
        .as("exclusive lower bound for %s.%s", schemaName, propertyName)
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  private record Endpoint(String method, String path) {
    @Override
    public String toString() {
      return method + " " + path;
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      JpaRepositoriesAutoConfiguration.class,
      FlywayAutoConfiguration.class,
      RedisAutoConfiguration.class,
      RedisRepositoriesAutoConfiguration.class,
      SecurityAutoConfiguration.class,
      SecurityFilterAutoConfiguration.class
  })
  @ComponentScan(
      basePackages = "com.i2i.cryptflow",
      useDefaultFilters = false,
      includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class)
  )
  @Import(OpenApiConfig.class)
  static class TestApplication {
  }
}
