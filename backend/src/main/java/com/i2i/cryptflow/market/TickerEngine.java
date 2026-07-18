package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.model.ExternalPriceProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Orchestrates periodic price updates.
 * In production, updates snapshots in database based on prices fetched from Redis.
 */
@Component
public class TickerEngine {
  private final MarketPriceService market;
  private final PriceSnapshotRepository snapshots;
  private final PriceSnapshotWriter writer;
  private final ExternalPriceProvider supportedSymbols;
  private final Map<String, BigDecimal> configuredPrices;

  public TickerEngine(
      MarketPriceService market,
      PriceSnapshotRepository snapshots,
      PriceSnapshotWriter writer,
      ExternalPriceProvider supportedSymbols,
      @Value("${app.ticker.initial-prices.BTC}") BigDecimal btc,
      @Value("${app.ticker.initial-prices.ETH}") BigDecimal eth,
      @Value("${app.ticker.initial-prices.SOL}") BigDecimal sol
  ) {
    this.market = market;
    this.snapshots = snapshots;
    this.writer = writer;
    this.supportedSymbols = supportedSymbols;
    this.configuredPrices = Map.of("BTC", btc, "ETH", eth, "SOL", sol);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void bootstrap() {
    try {
      market.getCurrent();
      return;
    } catch (Exception ignored) {
      // Redis is empty or incomplete; restore a complete market below.
    }

    var prices = new LinkedHashMap<String, BigDecimal>();
    boolean usedInitialPrice = false;

    for (var symbol : supportedSymbols.getSymbols()) {
      BigDecimal fetched = supportedSymbols.getInitialPrice(symbol);
      if (fetched != null) {
        prices.put(symbol, fetched);
      } else {
        var latest = snapshots.findFirstBySymbolOrderByRecordedAtDesc(symbol);
        if (latest.isPresent()) {
          prices.put(symbol, latest.get().getPriceUsd());
        } else {
          prices.put(symbol, getInitialPrice(symbol));
          usedInitialPrice = true;
        }
      }
    }

    var now = Instant.now();
    market.overwrite(prices, now);
    if (usedInitialPrice) {
      writer.write(prices, now);
    }
  }

  @Scheduled(fixedDelayString="${app.ticker.interval-ms}")
  public void tick() {
    MarketPrices current;
    try {
      current = market.getCurrent();
    } catch (Exception exception) {
      bootstrap();
      return;
    }

    var prices = new LinkedHashMap<String, BigDecimal>();
    for (var symbol : supportedSymbols.getSymbols()) {
      var price = current.prices().get(symbol);
      if (price != null) {
        prices.put(symbol, price);
      }
    }
    if (!prices.isEmpty()) {
      writer.write(prices, Instant.now());
    }
  }

  private BigDecimal getInitialPrice(String symbol) {
    if (configuredPrices.containsKey(symbol)) {
      return configuredPrices.get(symbol);
    }
    BigDecimal price = supportedSymbols.getInitialPrice(symbol);
    if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
      return price;
    }
    return new BigDecimal("1.00");
  }
}
