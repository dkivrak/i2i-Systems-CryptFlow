package com.i2i.cryptflow.portfolio;

import com.i2i.cryptflow.chat.ChatService;
import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {
  private final WalletRepository wallets;
  private final PortfolioAssetRepository assets;
  private final MarketPriceService market;
  private final ChatService chatService;
  private final EquityHistoryRepository equityHistory;

  private final Map<UUID, AdviceCache> adviceCache = new ConcurrentHashMap<>();
  private static final long CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes

  private record AdviceCache(String advice, long timestamp) {}

  public PortfolioController(
      WalletRepository wallets,
      PortfolioAssetRepository assets,
      MarketPriceService market,
      ChatService chatService,
      EquityHistoryRepository equityHistory
  ) {
    this.wallets = wallets;
    this.assets = assets;
    this.market = market;
    this.chatService = chatService;
    this.equityHistory = equityHistory;
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
              a.getQuantity().multiply(price).setScale(2, RoundingMode.HALF_UP),
              a.getAveragePrice());
        })
        .toList();
    var assetTotal = items.stream().map(AssetItem::valueUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new PortfolioResponse(wallet.getUsdBalance(), items, assetTotal, wallet.getUsdBalance().add(assetTotal));
  }

  @GetMapping("/ai-advice")
  Map<String, String> getAiAdvice(@AuthenticationPrincipal UUID userId, @RequestParam(defaultValue = "en") String lang) {
    long now = System.currentTimeMillis();
    var cached = adviceCache.get(userId);
    if (cached != null && (now - cached.timestamp() < CACHE_TTL_MS)) {
      return Map.of("advice", cached.advice());
    }

    String prompt = lang.equalsIgnoreCase("tr")
        ? "Lütfen portföyüm için 3-4 cümlelik risk analizi ve varlık yeniden dengeleme önerisi yaz. Başka hiçbir şey yazma, sadece tavsiye olsun."
        : "Please write a 3-4 sentence risk analysis and asset rebalancing recommendation for my portfolio. Write nothing else, only the advice.";

    var response = chatService.query(userId, prompt);
    String cleanAdvice = response.answer().replace("Educational purposes only — not financial advice.", "").trim();
    
    adviceCache.put(userId, new AdviceCache(cleanAdvice, now));
    return Map.of("advice", cleanAdvice);
  }

  @GetMapping("/equity-history")
  List<EquityPoint> getEquityHistory(@AuthenticationPrincipal UUID userId) {
    var history = equityHistory.findByUserIdOrderByRecordedAtAsc(userId);
    if (history.isEmpty()) {
      var wallet = wallets.findByUserId(userId).orElseThrow();
      return List.of(
          new EquityPoint(Instant.now().minusSeconds(3600 * 24), wallet.getUsdBalance()),
          new EquityPoint(Instant.now(), wallet.getUsdBalance())
      );
    }
    return history.stream().map(h -> new EquityPoint(h.getRecordedAt(), h.getTotalValue())).toList();
  }

  public record EquityPoint(Instant time, BigDecimal value) {}
  public record AssetItem(String symbol, BigDecimal quantity, BigDecimal priceUsd, BigDecimal valueUsd, BigDecimal averagePrice) {}
  public record PortfolioResponse(BigDecimal usdBalance, List<AssetItem> assets, BigDecimal assetValueUsd, BigDecimal totalValueUsd) {}
}
