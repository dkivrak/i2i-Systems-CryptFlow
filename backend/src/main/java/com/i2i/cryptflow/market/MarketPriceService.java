package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.SupportedSymbolsService;
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
  private final SupportedSymbolsService supportedSymbols;

  public MarketPriceService(StringRedisTemplate redis, SupportedSymbolsService supportedSymbols) {
    this.redis = redis;
    this.supportedSymbols = supportedSymbols;
  }

  public MarketPrices getCurrent() {
    var entries = redis.opsForHash().entries(KEY);
    var prices = new LinkedHashMap<String, BigDecimal>();
    for (var symbol : supportedSymbols.getSymbols()) {
      var value = entries.get(symbol);
      if (value == null) throw unavailable();
      prices.put(symbol, new BigDecimal(value.toString()));
    }
    var updated = entries.get(UPDATED);
    if (updated == null) throw unavailable();
    return new MarketPrices(prices, Instant.parse(updated.toString()));
  }

  public BigDecimal price(String symbol) {
    return getCurrent().prices().get(symbol);
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
