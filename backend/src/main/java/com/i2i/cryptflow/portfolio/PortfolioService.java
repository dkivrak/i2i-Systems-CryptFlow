package com.i2i.cryptflow.portfolio;

import com.i2i.cryptflow.chat.ChatService;
import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PortfolioService {
  private static final long CACHE_TTL_MS = 10 * 60 * 1000;

  private final WalletRepository wallets;
  private final PortfolioAssetRepository assets;
  private final MarketPriceService market;
  private final ChatService chatService;
  private final EquityHistoryRepository equityHistory;
  private final Map<UUID, AdviceCache> adviceCache = new ConcurrentHashMap<>();

  public PortfolioService(
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

  public PortfolioSnapshot get(UUID userId) {
    var wallet = wallets.findByUserId(userId).orElseThrow();
    var prices = market.getCurrent().prices();
    var items = assets.findByWalletIdOrderBySymbol(wallet.getId()).stream()
        .map(asset -> {
          var price = prices.get(asset.getSymbol());
          return new AssetValue(
              asset.getSymbol(),
              asset.getQuantity(),
              price,
              asset.getQuantity().multiply(price).setScale(2, RoundingMode.HALF_UP),
              asset.getAveragePrice());
        })
        .toList();
    var assetTotal = items.stream().map(AssetValue::valueUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new PortfolioSnapshot(wallet.getUsdBalance(), items, assetTotal, wallet.getUsdBalance().add(assetTotal));
  }

  public String getAiAdvice(UUID userId, String lang, boolean force) {
    long now = System.currentTimeMillis();
    var cached = adviceCache.get(userId);
    if (!force && cached != null && cached.lang().equalsIgnoreCase(lang)
        && now - cached.timestamp() < CACHE_TTL_MS) {
      return cached.advice();
    }

    String prompt = lang.equalsIgnoreCase("tr")
        ? "Lütfen portföyüm için 3-4 cümlelik risk analizi ve varlık yeniden dengeleme önerisi yaz. Başka hiçbir şey yazma, sadece tavsiye olsun."
        : "Please write a 3-4 sentence risk analysis and asset rebalancing recommendation for my portfolio. Write nothing else, only the advice.";

    var response = chatService.query(userId, prompt);
    String cleanAdvice = response.answer().replace("Educational purposes only — not financial advice.", "").trim();
    adviceCache.put(userId, new AdviceCache(cleanAdvice, lang, now));
    return cleanAdvice;
  }

  public List<EquityValue> getEquityHistory(UUID userId) {
    var history = equityHistory.findByUserIdOrderByRecordedAtAsc(userId);
    if (history.isEmpty()) {
      var totalValue = calculateTotalValue(userId);
      return List.of(
          new EquityValue(Instant.now().minusSeconds(3600 * 24), totalValue),
          new EquityValue(Instant.now(), totalValue)
      );
    }
    return history.stream()
        .map(value -> new EquityValue(value.getRecordedAt(), value.getTotalValue()))
        .toList();
  }

  private BigDecimal calculateTotalValue(UUID userId) {
    var wallet = wallets.findByUserId(userId).orElseThrow();
    var prices = market.getCurrent().prices();
    var assetTotal = assets.findByWalletIdOrderBySymbol(wallet.getId()).stream()
        .map(asset -> asset.getQuantity().multiply(prices.get(asset.getSymbol())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return wallet.getUsdBalance().add(assetTotal);
  }

  private record AdviceCache(String advice, String lang, long timestamp) {}

  public record AssetValue(
      String symbol,
      BigDecimal quantity,
      BigDecimal priceUsd,
      BigDecimal valueUsd,
      BigDecimal averagePrice
  ) {}

  public record PortfolioSnapshot(
      BigDecimal usdBalance,
      List<AssetValue> assets,
      BigDecimal assetValueUsd,
      BigDecimal totalValueUsd
  ) {}

  public record EquityValue(Instant time, BigDecimal value) {}
}
