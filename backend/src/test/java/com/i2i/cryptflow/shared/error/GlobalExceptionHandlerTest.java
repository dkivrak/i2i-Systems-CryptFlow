package com.i2i.cryptflow.shared.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {
  @Test
  void mapsMissingResourcesToStructuredNotFoundResponse() {
    var response = new GlobalExceptionHandler().handleNotFound(
        new NoResourceFoundException(HttpMethod.GET, "/missing")
    );

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("NOT_FOUND", response.getBody().code());
    assertEquals("Resource not found.", response.getBody().message());
  }
}
