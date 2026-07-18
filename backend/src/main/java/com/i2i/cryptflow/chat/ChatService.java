package com.i2i.cryptflow.chat;

import com.i2i.cryptflow.market.*;
import com.i2i.cryptflow.portfolio.PortfolioAssetRepository;
import com.i2i.cryptflow.shared.model.ExternalPriceProvider;
import com.i2i.cryptflow.trade.TradeTransactionRepository;
import com.i2i.cryptflow.user.User;
import com.i2i.cryptflow.user.UserRepository;
import com.i2i.cryptflow.wallet.Wallet;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
  public static final String DISCLAIMER = "Educational purposes only — not financial advice.";
  
  private static final String SYSTEM_PROMPT = 
      "You are CryptFlow's educational crypto assistant. Only discuss the supplied account, portfolio, " +
      "trades, market trends, and educational crypto insights. Never claim certainty or provide financial advice. " +
      "Answer in the language of the user's question.\n\n";

  private final GeminiClient gemini;
  private final UserRepository users;
  private final WalletRepository wallets;
  private final PortfolioAssetRepository assets;
  private final TradeTransactionRepository trades;
  private final PriceSnapshotRepository snapshots;
  private final MarketPriceService market;
  private final ExternalPriceProvider supportedSymbols;

  public ChatService(
      GeminiClient gemini,
      UserRepository users,
      WalletRepository wallets,
      PortfolioAssetRepository assets,
      TradeTransactionRepository trades,
      PriceSnapshotRepository snapshots,
      MarketPriceService market,
      ExternalPriceProvider supportedSymbols
  ) {
    this.gemini = gemini;
    this.users = users;
    this.wallets = wallets;
    this.assets = assets;
    this.trades = trades;
    this.snapshots = snapshots;
    this.market = market;
    this.supportedSymbols = supportedSymbols;
  }

  @Transactional(readOnly = true)
  public ChatResponse query(UUID userId, String message) {
    var user = users.findById(userId).orElseThrow();
    var wallet = wallets.findByUserId(userId).orElseThrow();
    
    var prompt = buildPrompt(user, wallet, message);
    var answer = gemini.generate(prompt);
    answer = ensureDisclaimer(answer);

    return new ChatResponse(answer, DISCLAIMER, Instant.now());
  }

  private String buildPrompt(User user, Wallet wallet, String message) {
    var prompt = new StringBuilder(SYSTEM_PROMPT)
      .append("USER EMAIL: ").append(user.getEmail())
      .append("\nUSD BALANCE: ").append(wallet.getUsdBalance())
      .append("\nPORTFOLIO: ").append(formatPortfolio(wallet.getId()))
      .append("\nCURRENT PRICES: ").append(market.getCurrent())
      .append("\nRECENT TRADES: ").append(formatRecentTrades(user.getId()));
    
    for (var symbol : supportedSymbols.getSymbols()) {
      prompt.append("\n").append(symbol).append(" RECENT PRICES: ").append(formatPriceHistory(symbol));
    }
    
    prompt.append("\n\nUSER QUESTION: ").append(message)
      .append("\nInclude this exact final line: ").append(DISCLAIMER);
      
    return prompt.toString();
  }

  private String formatPortfolio(UUID walletId) {
    return assets.findByWalletIdOrderBySymbol(walletId).stream()
        .map(a -> a.getSymbol() + "=" + a.getQuantity())
        .toList()
        .toString();
  }

  private String formatRecentTrades(UUID userId) {
    return trades.findTop20ByUserIdOrderByExecutedAtDesc(userId).stream()
        .map(t -> t.getSide() + " " + t.getQuantity() + " " + t.getSymbol() + " @ " + t.getUnitPriceUsd())
        .toList()
        .toString();
  }

  private String formatPriceHistory(String symbol) {
    return snapshots.findTop20BySymbolOrderByRecordedAtDesc(symbol).stream()
        .map(PriceSnapshot::getPriceUsd)
        .toList()
        .toString();
  }

  private String ensureDisclaimer(String answer) {
    if (!answer.contains(DISCLAIMER)) {
      return answer + "\n\n" + DISCLAIMER;
    }
    return answer;
  }

  public record ChatResponse(String answer, String disclaimer, Instant generatedAt) {}
}
