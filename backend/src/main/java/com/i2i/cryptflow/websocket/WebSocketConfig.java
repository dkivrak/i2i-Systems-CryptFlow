package com.i2i.cryptflow.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration @EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  private final String[] origins;
  public WebSocketConfig(@Value("${app.frontend-origins}") String origins){
    this.origins=java.util.Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isBlank()).toArray(String[]::new);
  }
  @Override public void configureMessageBroker(MessageBrokerRegistry registry){registry.enableSimpleBroker("/topic");registry.setApplicationDestinationPrefixes("/app");}
  @Override public void registerStompEndpoints(StompEndpointRegistry registry){registry.addEndpoint("/ws").setAllowedOrigins(origins);}
}
