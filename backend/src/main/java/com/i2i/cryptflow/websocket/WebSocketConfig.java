package com.i2i.cryptflow.websocket;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final PriceWebSocketHandler priceWebSocketHandler;
  private final String[] allowedOrigins;

  public WebSocketConfig(
      PriceWebSocketHandler priceWebSocketHandler,
      @Value("${app.frontend-origins}") String frontendOrigins
  ) {
    this.priceWebSocketHandler = priceWebSocketHandler;
    this.allowedOrigins = Arrays.stream(frontendOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isBlank())
        .toArray(String[]::new);
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(priceWebSocketHandler, "/ws").setAllowedOrigins(allowedOrigins);
  }
}
