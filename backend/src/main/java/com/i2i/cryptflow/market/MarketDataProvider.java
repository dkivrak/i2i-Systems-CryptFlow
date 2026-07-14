package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.model.AssetSymbol;
import java.math.BigDecimal;
import java.util.Map;

/**
 * External data provider abstraction for market prices.
 * Decouples the core business logic from the concrete data source (live API or ticker simulator).
 * Any implementation must produce current prices for all supported asset symbols.
 */
public interface MarketDataProvider {

    /**
     * Generates and returns the next set of market prices.
     * The returned map must contain an entry for every {@link AssetSymbol}.
     *
     * @param currentPrices the most recent known prices; may be empty on first invocation
     * @return a complete map of updated prices for all supported symbols
     */
    Map<AssetSymbol, BigDecimal> nextPrices(Map<AssetSymbol, BigDecimal> currentPrices);
}
