package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.model.AssetSymbol;
import java.math.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simulated market data provider that generates realistic random price fluctuations.
 * Implements {@link MarketDataProvider} to decouple the data source from core business logic.
 * Runs on a background scheduler thread managed by {@link TickerEngine}.
 */
@Component
public class SimulatedMarketDataProvider implements MarketDataProvider {

    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");

    private final BigDecimal maxChange;
    private final Map<AssetSymbol, BigDecimal> initialPrices;

    public SimulatedMarketDataProvider(
            @Value("${app.ticker.max-change-percent}") BigDecimal maxChange,
            @Value("${app.ticker.initial-prices.BTC}") BigDecimal btc,
            @Value("${app.ticker.initial-prices.ETH}") BigDecimal eth,
            @Value("${app.ticker.initial-prices.SOL}") BigDecimal sol) {
        this.maxChange = maxChange;
        this.initialPrices = Map.of(AssetSymbol.BTC, btc, AssetSymbol.ETH, eth, AssetSymbol.SOL, sol);
    }

    @Override
    public Map<AssetSymbol, BigDecimal> nextPrices(Map<AssetSymbol, BigDecimal> currentPrices) {
        var base = currentPrices.isEmpty() ? initialPrices : currentPrices;
        var next = new EnumMap<AssetSymbol, BigDecimal>(AssetSymbol.class);
        for (var symbol : AssetSymbol.values()) {
            double delta = ThreadLocalRandom.current().nextDouble(
                    maxChange.negate().doubleValue(), maxChange.doubleValue());
            var factor = BigDecimal.ONE.add(BigDecimal.valueOf(delta).movePointLeft(2));
            next.put(symbol, base.get(symbol).multiply(factor)
                    .max(MIN_PRICE)
                    .setScale(2, RoundingMode.HALF_UP));
        }
        return next;
    }

    public Map<AssetSymbol, BigDecimal> getInitialPrices() {
        return initialPrices;
    }
}
