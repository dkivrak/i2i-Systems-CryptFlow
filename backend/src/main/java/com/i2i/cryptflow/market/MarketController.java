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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
@Tag(name = "Market", description = "Current cached prices and persisted recent price history.")
public class MarketController {
  private final MarketPriceService market;

  public MarketController(MarketPriceService market) {
    this.market = market;
  }

  @GetMapping("/prices")
  @Operation(summary = "Get current market prices", description = "Public endpoint returning the latest Redis-backed prices for the currently supported symbols.")
  @ApiResponse(
      responseCode = "200",
      description = "Current prices and their last update time.",
      content = @Content(
          schema = @Schema(implementation = MarketPrices.class),
          examples = @ExampleObject(value = "{\"prices\":{\"BTC\":60000.00,\"ETH\":3000.00,\"SOL\":150.00},\"updatedAt\":\"2026-07-19T12:00:00Z\"}")
      )
  )
  MarketPrices prices(){return market.getCurrent();}

  @GetMapping("/history/{symbol}")
  @Operation(
      summary = "Get recent price history",
      description = "Returns at most 40 persisted snapshots in chronological order. An unknown symbol returns an empty array.",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Recent snapshots for the requested symbol.",
          content = @Content(
              array = @ArraySchema(schema = @Schema(implementation = PriceSnapshot.class)),
              examples = @ExampleObject(value = "[{\"symbol\":\"BTC\",\"priceUsd\":60000.00000000,\"recordedAt\":\"2026-07-19T12:00:00Z\"}]")
          )
      ),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  List<PriceSnapshot> history(
      @Parameter(description = "Asset symbol. Matching is case-insensitive for this history lookup.", example = "BTC", required = true)
      @PathVariable String symbol) {
    return market.recentHistory(symbol);
  }
}
