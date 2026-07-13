package com.i2i.cryptflow.shared.error;

import java.time.Instant;
import java.util.List;

public record ApiError(String code, String message, Instant timestamp, List<FieldError> fieldErrors) {
  public record FieldError(String field, String message) {}
}

