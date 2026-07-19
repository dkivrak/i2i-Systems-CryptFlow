package com.i2i.cryptflow.shared.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "ApiError", description = "Structured error returned by the REST API.")
public record ApiError(
    @Schema(description = "Stable machine-readable error code.", example = "VALIDATION_ERROR") String code,
    @Schema(description = "Human-readable error message.", example = "Please check the submitted fields.") String message,
    @Schema(description = "Time at which the error was produced.", example = "2026-07-19T12:00:00Z") Instant timestamp,
    @Schema(description = "Field-level validation details; empty for non-validation errors.") List<FieldError> fieldErrors
) {
  @Schema(name = "ApiFieldError", description = "Validation failure for one request field.")
  public record FieldError(
      @Schema(description = "Request field name.", example = "email") String field,
      @Schema(description = "Validation message.", example = "must be a well-formed email address") String message
  ) {}
}
