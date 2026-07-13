package com.i2i.cryptflow.shared.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigTest {
  @Test void allowsConfiguredFrontendOriginsAndRegisterHeaders() {
    var source = new SecurityConfig().corsConfigurationSource("http://localhost:5173,http://127.0.0.1:5173");
    var request = new MockHttpServletRequest("OPTIONS", "/api/auth/register");
    var cors = source.getCorsConfiguration(request);

    assertNotNull(cors);
    assertTrue(cors.getAllowedOrigins().contains("http://localhost:5173"));
    assertTrue(cors.getAllowedOrigins().contains("http://127.0.0.1:5173"));
    assertTrue(cors.getAllowedMethods().contains("POST"));
    assertTrue(cors.getAllowedMethods().contains("OPTIONS"));
    assertTrue(cors.getAllowedHeaders().contains("Authorization"));
    assertTrue(cors.getAllowedHeaders().contains("Content-Type"));
  }
}
