package com.i2i.cryptflow.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

class WebSocketConfigTest {
  @Test
  void registersConfiguredOriginsWithoutWildcard() {
    var handler = mock(PriceWebSocketHandler.class);
    var registry = mock(WebSocketHandlerRegistry.class);
    var registration = mock(WebSocketHandlerRegistration.class);
    when(registry.addHandler(handler, "/ws")).thenReturn(registration);

    new WebSocketConfig(
        handler,
        " http://localhost:5173, https://cryptflow.example , "
    ).registerWebSocketHandlers(registry);

    verify(registration).setAllowedOrigins(
        "http://localhost:5173",
        "https://cryptflow.example"
    );
  }
}
