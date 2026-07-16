package com.i2i.cryptflow.trade;

import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.portfolio.PortfolioAssetRepository;
import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.AssetSymbol;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeService {
  private static final int MAX_DECIMAL_PLACES = 8;
  private static final int MAX_PAGE_SIZE = 100;

  private final WalletRepository wallets;
  private final PortfolioAssetRepository assets;
  private final TradeTransactionRepository trades;
  private final MarketPriceService market;

  public TradeService(WalletRepository wallets, PortfolioAssetRepository assets, TradeTransactionRepository trades, MarketPriceService market) {
    this.wallets = wallets;
    this.assets = assets;
    this.trades = trades;
    this.market = market;
  }

  @Transactional
  public TradeResult execute(UUID userId, AssetSymbol symbol, TradeSide side, BigDecimal rawQuantity) {
    if (rawQuantity == null || rawQuantity.signum() <= 0 || Math.max(0, rawQuantity.stripTrailingZeros().scale()) > MAX_DECIMAL_PLACES)
      throw invalidAmount();
    var quantity = rawQuantity.setScale(MAX_DECIMAL_PLACES, RoundingMode.UNNECESSARY);
    var wallet = wallets.findByUserIdForUpdate(userId).orElseThrow();
    var asset = assets.findForUpdate(wallet.getId(), symbol).orElseThrow();
    var price = market.price(symbol).setScale(2, RoundingMode.HALF_UP);
    var total = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
    if (total.signum() <= 0) throw invalidAmount();
    if (side == TradeSide.BUY) {
      if (wallet.getUsdBalance().compareTo(total) < 0)
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Insufficient USD balance for this transaction.");
      wallet.setUsdBalance(wallet.getUsdBalance().subtract(total));
      asset.setQuantity(asset.getQuantity().add(quantity));
    } else {
      if (asset.getQuantity().compareTo(quantity) < 0)
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_ASSET_BALANCE", "Insufficient asset balance for sale.");
      asset.setQuantity(asset.getQuantity().subtract(quantity));
      wallet.setUsdBalance(wallet.getUsdBalance().add(total));
    }
    var trade = trades.save(new TradeTransaction(wallet.getUser(), wallet, symbol, side, quantity, price, total));
    return from(trade);
  }

  @Transactional(readOnly = true)
  public Page<TradeResult> history(UUID userId, int page, int size) {
    return trades.findByUserIdOrderByExecutedAtDesc(userId, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE))).map(this::from);
  }

  private TradeResult from(TradeTransaction transaction) {
    return new TradeResult(
        transaction.getId(),
        transaction.getSymbol(),
        transaction.getSide(),
        transaction.getQuantity(),
        transaction.getUnitPriceUsd(),
        transaction.getTotalUsd(),
        transaction.getExecutedAt());
  }

  private ApiException invalidAmount() {
    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Quantity must be positive and have at most " + MAX_DECIMAL_PLACES + " decimal places.");
  }

  public record TradeResult(UUID id, AssetSymbol symbol, TradeSide side, BigDecimal quantity, BigDecimal unitPriceUsd, BigDecimal totalUsd, Instant executedAt) {}
}
