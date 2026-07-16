package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.model.AssetSymbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
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
  private final Map<AssetSymbol, BigDecimal> initial;

  public TickerEngine(
      MarketPriceService market,
      PriceSnapshotRepository snapshots,
      PriceSnapshotWriter writer,
      @Value("${app.ticker.initial-prices.BTC}") BigDecimal btc,
      @Value("${app.ticker.initial-prices.ETH}") BigDecimal eth,
      @Value("${app.ticker.initial-prices.SOL}") BigDecimal sol
  ) {
    this.market = market;
    this.snapshots = snapshots;
    this.writer = writer;
    this.initial = Map.of(
        AssetSymbol.BTC, btc,
        AssetSymbol.ETH, eth,
        AssetSymbol.SOL, sol
    );
  }

  @EventListener(ApplicationReadyEvent.class)
  public void bootstrap() {
    try {
      market.getCurrent();
      return;
    } catch (Exception ignored) {
      // Redis is empty or incomplete; restore a complete market below.
    }

    var prices = new EnumMap<AssetSymbol, BigDecimal>(AssetSymbol.class);
    boolean usedInitialPrice = false;

    for (var symbol : AssetSymbol.values()) {
      var latest = snapshots.findFirstBySymbolOrderByRecordedAtDesc(symbol);
      if (latest.isPresent()) {
        prices.put(symbol, latest.get().getPriceUsd());
      } else {
        prices.put(symbol, initial.get(symbol));
        usedInitialPrice = true;
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

    var prices = new EnumMap<AssetSymbol, BigDecimal>(AssetSymbol.class);
    for (var symbol : AssetSymbol.values()) {
      prices.put(symbol, current.prices().get(symbol.name()));
    }
    writer.write(prices, Instant.now());
  }
}
