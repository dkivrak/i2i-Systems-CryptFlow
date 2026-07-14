package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.model.AssetSymbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Orchestrates periodic price updates using a {@link MarketDataProvider}.
 * The actual price generation logic is fully decoupled behind the provider interface,
 * allowing easy swap between simulated and live data sources.
 */
@Component
public class TickerEngine {

    private final MarketDataProvider dataProvider;
    private final MarketPriceService market;
    private final PriceSnapshotRepository snapshots;
    private final PriceSnapshotWriter writer;
    private final SimpMessagingTemplate messaging;

    public TickerEngine(MarketDataProvider dataProvider, MarketPriceService market,
                        PriceSnapshotRepository snapshots, PriceSnapshotWriter writer,
                        SimpMessagingTemplate messaging) {
        this.dataProvider = dataProvider;
        this.market = market;
        this.snapshots = snapshots;
        this.writer = writer;
        this.messaging = messaging;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        try { market.getCurrent(); return; } catch (Exception ignored) {}
        var prices = new EnumMap<AssetSymbol, BigDecimal>(AssetSymbol.class);
        boolean foundAll = true;
        for (var s : AssetSymbol.values()) {
            var latest = snapshots.findFirstBySymbolOrderByRecordedAtDesc(s);
            if (latest.isEmpty()) { foundAll = false; break; }
            prices.put(s, latest.get().getPriceUsd());
        }
        if (!foundAll) {
            var initial = dataProvider.nextPrices(Map.of());
            var now = Instant.now();
            writer.write(initial, now);
            market.overwrite(initial, now);
        } else {
            market.overwrite(prices, Instant.now());
        }
    }

    @Scheduled(fixedDelayString = "${app.ticker.interval-ms}")
    public void tick() {
        MarketPrices current;
        try { current = market.getCurrent(); } catch (Exception ex) { bootstrap(); return; }

        var currentMap = new EnumMap<AssetSymbol, BigDecimal>(AssetSymbol.class);
        for (var s : AssetSymbol.values()) {
            currentMap.put(s, current.prices().get(s.name()));
        }

        var next = dataProvider.nextPrices(currentMap);
        var now = Instant.now();
        writer.write(next, now);
        market.overwrite(next, now);
        messaging.convertAndSend("/topic/market/prices", market.getCurrent());
    }
}
