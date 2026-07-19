package com.i2i.cryptflow.portfolio;

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
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio")
@Tag(name = "Portfolio", description = "Authenticated holdings, valuation history, and Gemini-generated portfolio commentary.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PortfolioController {
  private final PortfolioService portfolioService;

  public PortfolioController(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
  }

  @GetMapping
  @Operation(summary = "Get the current portfolio", description = "Values holdings using current Redis-backed market prices and combines them with the virtual USD balance.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Current cash, holdings, and total valuation.",
          content = @Content(
              schema = @Schema(implementation = PortfolioResponse.class),
              examples = @ExampleObject(value = "{\"usdBalance\":14950.00,\"assets\":[{\"symbol\":\"BTC\",\"quantity\":0.00100000,\"priceUsd\":60000.00,\"valueUsd\":60.00,\"averagePrice\":59000.00000000}],\"assetValueUsd\":60.00,\"totalValueUsd\":15010.00}")
          )
      ),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  PortfolioResponse get(@AuthenticationPrincipal UUID userId) {
    var portfolio = portfolioService.get(userId);
    var items = portfolio.assets().stream()
        .map(asset -> new AssetItem(
            asset.symbol(),
            asset.quantity(),
            asset.priceUsd(),
            asset.valueUsd(),
            asset.averagePrice()))
        .toList();
    return new PortfolioResponse(
        portfolio.usdBalance(),
        items,
        portfolio.assetValueUsd(),
        portfolio.totalValueUsd());
  }

  @GetMapping("/ai-advice")
  @Operation(summary = "Get portfolio AI commentary", description = "Returns short Gemini-generated risk and rebalancing commentary. Successful results are cached in memory per user and language for 10 minutes.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Generated or cached commentary.",
          content = @Content(
              schema = @Schema(type = "object", example = "{\"advice\":\"Diversification can reduce concentration risk.\"}"),
              examples = @ExampleObject(value = "{\"advice\":\"Diversification can reduce concentration risk.\"}")
          )
      ),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "503", description = "Gemini configuration is missing or the Gemini service is unavailable.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  Map<String, String> getAiAdvice(
      @AuthenticationPrincipal UUID userId,
      @Parameter(description = "Prompt language. tr selects Turkish; every other value selects English.", example = "en")
      @RequestParam(defaultValue = "en") String lang,
      @Parameter(description = "Set true to bypass the 10-minute in-memory cache.", example = "false")
      @RequestParam(defaultValue = "false") boolean force
  ) {
    return Map.of("advice", portfolioService.getAiAdvice(userId, lang, force));
  }

  @GetMapping("/equity-history")
  @Operation(summary = "Get portfolio equity history", description = "Returns recorded total-value points in chronological order. When no history exists, two synthetic points with the current value are returned.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Chronological equity points.",
          content = @Content(
              array = @ArraySchema(schema = @Schema(implementation = EquityPoint.class)),
              examples = @ExampleObject(value = "[{\"time\":\"2026-07-18T12:00:00Z\",\"value\":15000.00},{\"time\":\"2026-07-19T12:00:00Z\",\"value\":15010.00}]")
          )
      ),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  List<EquityPoint> getEquityHistory(@AuthenticationPrincipal UUID userId) {
    return portfolioService.getEquityHistory(userId).stream()
        .map(value -> new EquityPoint(value.time(), value.value()))
        .toList();
  }

  @Schema(name = "EquityPoint", description = "Portfolio total value at one instant.")
  public record EquityPoint(
      @Schema(description = "Measurement time.", example = "2026-07-19T12:00:00Z", format = "date-time") Instant time,
      @Schema(description = "Total portfolio value in virtual USD.", example = "15010.00") BigDecimal value
  ) {}

  @Schema(name = "PortfolioAssetItem", description = "One held asset and its current valuation.")
  public record AssetItem(
      @Schema(description = "Uppercase asset symbol.", example = "BTC") String symbol,
      @Schema(description = "Quantity held.", example = "0.00100000") BigDecimal quantity,
      @Schema(description = "Current unit price in USD.", example = "60000.00") BigDecimal priceUsd,
      @Schema(description = "Current holding value in USD.", example = "60.00") BigDecimal valueUsd,
      @Schema(description = "Volume-weighted average acquisition price.", example = "59000.00000000") BigDecimal averagePrice
  ) {}

  @Schema(name = "PortfolioResponse", description = "Authenticated user's current virtual portfolio valuation.")
  public record PortfolioResponse(
      @Schema(description = "Available virtual USD cash.", example = "14950.00") BigDecimal usdBalance,
      @Schema(description = "Held assets ordered by symbol.") List<AssetItem> assets,
      @Schema(description = "Combined USD value of all assets.", example = "60.00") BigDecimal assetValueUsd,
      @Schema(description = "Cash plus asset value.", example = "15010.00") BigDecimal totalValueUsd
  ) {}
}
