package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.config.OpenApiConfig;
import com.i2i.cryptflow.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "Create and inspect authenticated price alerts.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AlertController {
  private final AlertService alertService;

  public AlertController(AlertService alertService) {
    this.alertService = alertService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a price alert", description = "Trims and normalizes the symbol and condition to uppercase. The scheduler triggers the alert when the current supported-symbol price is at or beyond the requested threshold.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Alert created.",
          content = @Content(
              schema = @Schema(implementation = AlertDto.class),
              examples = @ExampleObject(value = "{\"id\":\"7b212f51-53cb-4b9e-94d1-c12fc6d91e68\",\"symbol\":\"BTC\",\"targetPrice\":65000.00,\"condition\":\"ABOVE\",\"isTriggered\":false,\"createdAt\":\"2026-07-19T12:00:00Z\"}")
          )
      ),
      @ApiResponse(responseCode = "400", description = "Malformed body, unsupported symbol, blank fields, non-positive target price, or condition other than ABOVE/BELOW.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  AlertDto create(
      @AuthenticationPrincipal UUID userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "Alert symbol, positive threshold, and ABOVE/BELOW condition.",
          content = @Content(
              schema = @Schema(implementation = CreateAlertRequest.class),
              examples = @ExampleObject(value = "{\"symbol\":\"BTC\",\"targetPrice\":65000.00,\"condition\":\"ABOVE\"}")
          )
      )
      @Valid @RequestBody CreateAlertRequest req) {
    var alert = alertService.create(userId, req.symbol(), req.targetPrice(), req.condition());
    return from(alert);
  }

  @GetMapping
  @Operation(summary = "List active alerts")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Non-triggered alerts, newest first.", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AlertDto.class)))),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  List<AlertDto> getActive(@AuthenticationPrincipal UUID userId) {
    return alertService.getActive(userId).stream().map(this::from).toList();
  }

  @GetMapping("/triggered")
  @Operation(summary = "List triggered alerts")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Triggered alerts, newest first.", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AlertDto.class)))),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  List<AlertDto> getTriggered(@AuthenticationPrincipal UUID userId) {
    return alertService.getTriggered(userId).stream().map(this::from).toList();
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete an alert")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Alert deleted."),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "403", description = "The alert belongs to another user.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "404", description = "Alert not found.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  void delete(
      @AuthenticationPrincipal UUID userId,
      @Parameter(description = "Alert identifier.", example = "7b212f51-53cb-4b9e-94d1-c12fc6d91e68", required = true)
      @PathVariable UUID id) {
    alertService.delete(userId, id);
  }

  private AlertDto from(PriceAlert alert) {
    return new AlertDto(
        alert.getId(),
        alert.getSymbol(),
        alert.getTargetPrice(),
        alert.getCondition(),
        alert.isTriggered(),
        alert.getCreatedAt(),
        alert.getTriggeredAt()
    );
  }

  @Schema(name = "CreateAlertRequest", description = "Definition of a price threshold alert.")
  public record CreateAlertRequest(
      @Schema(description = "Asset symbol; stored in uppercase.", example = "BTC")
      @NotBlank String symbol,
      @Schema(description = "Strictly positive USD threshold.", example = "65000.00", minimum = "0", exclusiveMinimum = true)
      @NotNull @Positive BigDecimal targetPrice,
      @Schema(description = "Trigger when the current price is at or beyond the threshold in this direction.", example = "ABOVE", allowableValues = {"ABOVE", "BELOW"})
      @NotBlank String condition // ABOVE, BELOW
  ) {}

  @Schema(name = "Alert", description = "Stored price alert state.")
  public record AlertDto(
      @Schema(description = "Alert identifier.", example = "7b212f51-53cb-4b9e-94d1-c12fc6d91e68", format = "uuid") UUID id,
      @Schema(description = "Uppercase asset symbol.", example = "BTC") String symbol,
      @Schema(description = "USD trigger threshold.", example = "65000.00") BigDecimal targetPrice,
      @Schema(description = "Trigger direction.", example = "ABOVE", allowableValues = {"ABOVE", "BELOW"}) String condition,
      @Schema(description = "Whether the scheduler has triggered this alert.", example = "false") boolean isTriggered,
      @Schema(description = "Alert creation time.", example = "2026-07-19T12:00:00Z", format = "date-time") Instant createdAt,
      @Schema(description = "Trigger time; omitted from JSON until triggered.", example = "2026-07-19T13:00:00Z", format = "date-time", nullable = true) Instant triggeredAt
  ) {}
}
