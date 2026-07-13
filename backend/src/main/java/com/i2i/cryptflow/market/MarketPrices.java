package com.i2i.cryptflow.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record MarketPrices(Map<String, BigDecimal> prices, Instant updatedAt) {}

