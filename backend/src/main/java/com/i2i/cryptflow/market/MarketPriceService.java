package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.AssetSymbol;
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

  public MarketPriceService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public MarketPrices getCurrent() {
    var entries = redis.opsForHash().entries(KEY);
    var prices = new LinkedHashMap<String, BigDecimal>();
    for (var symbol : AssetSymbol.values()) {
      var value = entries.get(symbol.name());
      if (value == null) throw unavailable();
      prices.put(symbol.name(), new BigDecimal(value.toString()));
    }
    var updated = entries.get(UPDATED);
    if (updated == null) throw unavailable();
    return new MarketPrices(prices, Instant.parse(updated.toString()));
  }

  public BigDecimal price(AssetSymbol symbol) {
    return getCurrent().prices().get(symbol.name());
  }

  public void overwrite(Map<AssetSymbol, BigDecimal> prices, Instant time) {
    var values = new HashMap<String, String>();
    prices.forEach((s, p) -> values.put(s.name(), p.toPlainString()));
    values.put(UPDATED, time.toString());
    redis.delete(KEY);
    redis.opsForHash().putAll(KEY, values);
  }

  public void updateSinglePrice(AssetSymbol symbol, BigDecimal price, Instant time) {
    redis.opsForHash().put(KEY, symbol.name(), price.toPlainString());
    redis.opsForHash().put(KEY, UPDATED, time.toString());
  }

  private ApiException unavailable() {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MARKET_DATA_UNAVAILABLE", "Current market data is unavailable.");
  }
}
