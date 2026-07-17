package com.i2i.cryptflow.portfolio;

import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {
  private final WalletRepository wallets;
  private final PortfolioAssetRepository assets;
  private final MarketPriceService market;

  public PortfolioController(WalletRepository wallets, PortfolioAssetRepository assets, MarketPriceService market) {
    this.wallets = wallets;
    this.assets = assets;
    this.market = market;
  }

  @GetMapping
  PortfolioResponse get(@AuthenticationPrincipal UUID userId) {
    var wallet = wallets.findByUserId(userId).orElseThrow();
    var prices = market.getCurrent().prices();
    var items = assets.findByWalletIdOrderBySymbol(wallet.getId()).stream()
        .map(a -> {
          var price = prices.get(a.getSymbol());
          return new AssetItem(
              a.getSymbol(),
              a.getQuantity(),
              price,
              a.getQuantity().multiply(price).setScale(2, RoundingMode.HALF_UP));
        })
        .toList();
    var assetTotal = items.stream().map(AssetItem::valueUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new PortfolioResponse(wallet.getUsdBalance(), items, assetTotal, wallet.getUsdBalance().add(assetTotal));
  }

  public record AssetItem(String symbol, BigDecimal quantity, BigDecimal priceUsd, BigDecimal valueUsd) {}
  public record PortfolioResponse(BigDecimal usdBalance, List<AssetItem> assets, BigDecimal assetValueUsd, BigDecimal totalValueUsd) {}
}
