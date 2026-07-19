package com.i2i.cryptflow.trade;

import com.i2i.cryptflow.shared.config.OpenApiConfig;
import com.i2i.cryptflow.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trades")
@Tag(name = "Trades", description = "Execute immediate paper trades and inspect paginated trade history.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class TradeController {
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final TradeService service;

  public TradeController(TradeService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Execute a market trade", description = "Executes atomically at the latest cached price. The symbol is trimmed and normalized to uppercase, and quantity may have at most eight decimal places.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Trade executed.",
          content = @Content(
              schema = @Schema(implementation = TradeService.TradeResult.class),
              examples = @ExampleObject(value = "{\"id\":\"3dc8e06d-7fd6-4c52-8ada-70521d94682d\",\"symbol\":\"BTC\",\"side\":\"BUY\",\"quantity\":0.00100000,\"unitPriceUsd\":60000.00000000,\"totalUsd\":60.00,\"executedAt\":\"2026-07-19T12:00:00Z\"}")
          )
      ),
      @ApiResponse(responseCode = "400", description = "Malformed body, unsupported symbol, invalid side, non-positive quantity, more than eight decimal places, or total below $0.01.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "422", description = "Insufficient virtual USD or asset quantity.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  TradeService.TradeResult execute(
      @AuthenticationPrincipal UUID userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "Immediate BUY or SELL request.",
          content = @Content(
              schema = @Schema(implementation = TradeRequest.class),
              examples = @ExampleObject(value = "{\"symbol\":\"BTC\",\"side\":\"BUY\",\"quantity\":0.00100000}")
          )
      )
      @Valid @RequestBody TradeRequest r) {
    return service.execute(userId, r.symbol(), r.side(), r.quantity());
  }

  @GetMapping
  @Operation(summary = "Get trade history", description = "Returns newest trades first. Negative page values are treated as 0; size is clamped to the range 1 through 100.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Spring Data page containing trade results and pagination metadata.",
          content = @Content(
              schema = @Schema(implementation = TradePageResponse.class),
              examples = @ExampleObject(value = "{\"content\":[{\"id\":\"3dc8e06d-7fd6-4c52-8ada-70521d94682d\",\"symbol\":\"BTC\",\"side\":\"BUY\",\"quantity\":0.00100000,\"unitPriceUsd\":60000.00000000,\"totalUsd\":60.00,\"executedAt\":\"2026-07-19T12:00:00Z\"}],\"pageable\":{\"pageNumber\":0,\"pageSize\":20,\"offset\":0,\"paged\":true,\"unpaged\":false,\"sort\":{\"empty\":true,\"sorted\":false,\"unsorted\":true}},\"totalElements\":1,\"totalPages\":1,\"size\":20,\"number\":0,\"numberOfElements\":1,\"first\":true,\"last\":true,\"empty\":false,\"sort\":{\"empty\":true,\"sorted\":false,\"unsorted\":true}}")
          )
      ),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  Page<TradeService.TradeResult> history(
      @AuthenticationPrincipal UUID userId,
      @Parameter(description = "Zero-based page index. Negative values are normalized to 0.", example = "0")
      @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Requested page size, normalized into the range 1 through 100.", example = "20")
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    return service.history(userId, page, size);
  }

  @Schema(name = "TradeRequest", description = "Immediate paper-trade request.")
  public record TradeRequest(
      @Schema(description = "Supported asset symbol; trimmed and normalized to uppercase.", example = "BTC")
      @NotBlank String symbol,
      @Schema(description = "Trade direction.", example = "BUY", allowableValues = {"BUY", "SELL"})
      @NotNull TradeSide side,
      @Schema(description = "Strictly positive asset quantity with at most eight decimal places.", example = "0.00100000", minimum = "0", exclusiveMinimum = true, multipleOf = 0.00000001)
      @NotNull BigDecimal quantity
  ) {}

  @Schema(name = "TradePage", description = "Spring Data pagination envelope for trade history.")
  public record TradePageResponse(
      List<TradeService.TradeResult> content,
      PageMetadata pageable,
      boolean last,
      long totalElements,
      int totalPages,
      boolean first,
      int size,
      int number,
      SortMetadata sort,
      int numberOfElements,
      boolean empty
  ) {}

  @Schema(name = "PageMetadata", description = "Requested page and offset details.")
  public record PageMetadata(
      int pageNumber,
      int pageSize,
      SortMetadata sort,
      long offset,
      boolean paged,
      boolean unpaged
  ) {}

  @Schema(name = "SortMetadata", description = "Sort state included by Spring Data.")
  public record SortMetadata(boolean empty, boolean sorted, boolean unsorted) {}
}
