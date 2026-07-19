package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.ExternalPriceProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MarketPriceService {
  private static final String KEY = "market:prices";
  private static final String UPDATED = "updatedAt";

  private final StringRedisTemplate redis;
  private final ExternalPriceProvider supportedSymbols;
  private final PriceSnapshotRepository snapshots;

  public MarketPriceService(
      StringRedisTemplate redis,
      ExternalPriceProvider supportedSymbols,
      PriceSnapshotRepository snapshots
  ) {
    this.redis = redis;
    this.supportedSymbols = supportedSymbols;
    this.snapshots = snapshots;
  }

  public MarketPrices getCurrent() {
    var entries = redis.opsForHash().entries(KEY);
    var prices = new LinkedHashMap<String, BigDecimal>();
    boolean missingAny = false;

    for (var symbol : supportedSymbols.getSymbols()) {
      var value = entries.get(symbol);
      if (value != null) {
        prices.put(symbol, new BigDecimal(value.toString()));
      } else {
        BigDecimal fallback = supportedSymbols.getInitialPrice(symbol);
        if (fallback == null) {
          fallback = new BigDecimal("1.00");
        }
        prices.put(symbol, fallback);
        redis.opsForHash().put(KEY, symbol, fallback.toPlainString());
        missingAny = true;
      }
    }

    var updated = entries.get(UPDATED);
    Instant updateTime = updated != null ? Instant.parse(updated.toString()) : Instant.now();
    if (updated == null || missingAny) {
      redis.opsForHash().put(KEY, UPDATED, updateTime.toString());
    }

    return new MarketPrices(prices, updateTime);
  }

  public BigDecimal price(String symbol) {
    return getCurrent().prices().get(symbol);
  }

  public List<PriceSnapshot> recentHistory(String symbol) {
    var history = new ArrayList<>(snapshots.findTop40BySymbolOrderByRecordedAtDesc(symbol.toUpperCase()));
    Collections.reverse(history);
    return history;
  }

  public void overwrite(Map<String, BigDecimal> prices, Instant time) {
    var values = new HashMap<String, String>();
    prices.forEach((s, p) -> values.put(s, p.toPlainString()));
    values.put(UPDATED, time.toString());
    redis.delete(KEY);
    redis.opsForHash().putAll(KEY, values);
  }

  public void updateSinglePrice(String symbol, BigDecimal price, Instant time) {
    redis.opsForHash().put(KEY, symbol, price.toPlainString());
    redis.opsForHash().put(KEY, UPDATED, time.toString());
  }

  private ApiException unavailable() {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_DATA_UNAVAILABLE", "Current market data is unavailable.");
  }
}
