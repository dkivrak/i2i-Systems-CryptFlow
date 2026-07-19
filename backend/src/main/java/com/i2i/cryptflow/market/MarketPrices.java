package com.i2i.cryptflow.market;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Schema(name = "MarketPrices", description = "Latest prices cached for the currently supported asset symbols.")
public record MarketPrices(
    @Schema(description = "Map from uppercase asset symbol to its USD price.", example = "{\"BTC\":60000.00,\"ETH\":3000.00}") Map<String, BigDecimal> prices,
    @Schema(description = "Time associated with the latest cache update.", example = "2026-07-19T12:00:00Z", format = "date-time") Instant updatedAt
) {}
