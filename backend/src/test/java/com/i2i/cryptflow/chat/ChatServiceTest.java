package com.i2i.cryptflow.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.market.MarketPrices;
import com.i2i.cryptflow.market.PriceSnapshot;
import com.i2i.cryptflow.market.PriceSnapshotRepository;
import com.i2i.cryptflow.portfolio.PortfolioAsset;
import com.i2i.cryptflow.portfolio.PortfolioAssetRepository;
import com.i2i.cryptflow.shared.model.AssetSymbol;
import com.i2i.cryptflow.trade.TradeSide;
import com.i2i.cryptflow.trade.TradeTransaction;
import com.i2i.cryptflow.trade.TradeTransactionRepository;
import com.i2i.cryptflow.user.User;
import com.i2i.cryptflow.wallet.Wallet;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatServiceTest {
  private final GeminiClient gemini = mock(GeminiClient.class);
  private final WalletRepository wallets = mock(WalletRepository.class);
  private final PortfolioAssetRepository assets = mock(PortfolioAssetRepository.class);
  private final TradeTransactionRepository trades = mock(TradeTransactionRepository.class);
  private final PriceSnapshotRepository snapshots = mock(PriceSnapshotRepository.class);
  private final MarketPriceService market = mock(MarketPriceService.class);
  private final ChatService service = new ChatService(
      gemini, wallets, assets, trades, snapshots, market);

  @BeforeEach
  void respondFromGemini() {
    when(gemini.generate(anyString(), anyString())).thenReturn("Gemini yanıtı");
  }

  @Test
  void greetingUsesNoFinancialContextAndRequestsShortNaturalReply() {
    ChatService.ChatResponse response = service.query(UUID.randomUUID(), "Naber, nasıl gidiyor?");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.system().contains("REQUEST_MODE: SOCIAL"));
    assertTrue(prompt.system().contains("one or two short sentences"));
    assertTrue(prompt.user().contains("Naber, nasıl gidiyor?"));
    assertNoFinancialSections(prompt.user());
    verifyNoInteractions(wallets, assets, trades, snapshots, market);
    assertDisclaimer(response);
  }

  @Test
  void outOfScopeQuestionUsesNoFinancialContextAndExplainsSupportedScope() {
    ChatService.ChatResponse response = service.query(UUID.randomUUID(), "Bana makarna tarifi ver");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.system().contains("REQUEST_MODE: OUT_OF_SCOPE"));
    assertTrue(prompt.system().contains("outside your scope"));
    assertTrue(prompt.system().contains("portfolio, trades, market data, and crypto education"));
    assertNoFinancialSections(prompt.user());
    verifyNoInteractions(wallets, assets, trades, snapshots, market);
    assertDisclaimer(response);
  }

  @Test
  void generalBuyQuestionDoesNotExposeTradeHistory() {
    service.query(UUID.randomUUID(), "Should I buy a car?");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.system().contains("REQUEST_MODE: OUT_OF_SCOPE"));
    assertNoFinancialSections(prompt.user());
    verifyNoInteractions(wallets, assets, trades, snapshots, market);
  }

  @Test
  void genericUsdQuestionDoesNotExposeAccountCash() {
    service.query(UUID.randomUUID(), "1 USD kaç TL?");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.system().contains("REQUEST_MODE: OUT_OF_SCOPE"));
    assertNoFinancialSections(prompt.user());
    verifyNoInteractions(wallets, assets, trades, snapshots, market);
  }

  @Test
  void unrelatedWordStartingWithParamDoesNotExposeAccountCash() {
    service.query(UUID.randomUUID(), "Parametre nedir?");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.system().contains("REQUEST_MODE: OUT_OF_SCOPE"));
    assertNoFinancialSections(prompt.user());
    verifyNoInteractions(wallets, assets, trades, snapshots, market);
  }

  @Test
  void nonFinancialEnglishBalanceQuestionDoesNotExposeAccountCash() {
    service.query(UUID.randomUUID(), "How can I improve my work-life balance?");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.system().contains("REQUEST_MODE: OUT_OF_SCOPE"));
    assertNoFinancialSections(prompt.user());
    verifyNoInteractions(wallets, assets, trades, snapshots, market);
  }

  @Test
  void cryptoEducationUsingBalanceAsVerbDoesNotLoadPrivateContext() {
    service.query(UUID.randomUUID(), "How does blockchain balance scalability and security?");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.system().contains("REQUEST_MODE: SUPPORTED"));
    assertNoFinancialSections(prompt.user());
    verifyNoInteractions(wallets, assets, trades, snapshots, market);
  }

  @Test
  void turkishDirectionWordSolIsNotTreatedAsTheSolanaSymbol() {
    service.query(UUID.randomUUID(), "Sol tarafım ağrıyor");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.system().contains("REQUEST_MODE: OUT_OF_SCOPE"));
    assertNoFinancialSections(prompt.user());
    verifyNoInteractions(wallets, assets, trades, snapshots, market);
  }

  @Test
  void portfolioSummaryLoadsHoldingsAndCurrentPricesButNotUnrequestedHistory() {
    User user = new User("scope-test@example.com", "hash");
    Wallet wallet = new Wallet(user, new BigDecimal("12500.00"));
    PortfolioAsset btc = new PortfolioAsset(wallet, AssetSymbol.BTC);
    btc.setQuantity(new BigDecimal("0.25000000"));
    UUID userId = user.getId();
    when(wallets.findByUserId(userId)).thenReturn(Optional.of(wallet));
    when(assets.findByWalletIdOrderBySymbol(wallet.getId())).thenReturn(List.of(btc));
    when(market.getCurrent()).thenReturn(new MarketPrices(
        Map.of(
            "BTC", new BigDecimal("61000.00"),
            "ETH", new BigDecimal("3200.00"),
            "SOL", new BigDecimal("155.00")),
        Instant.parse("2026-07-14T10:00:00Z")));

    ChatService.ChatResponse response = service.query(userId, "Portföyümü özetle");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.user().contains("[PORTFOLIO_ASSETS]"));
    assertTrue(prompt.user().contains("BTC quantity=0.25000000"));
    assertTrue(prompt.user().contains("[CURRENT_MARKET_PRICES]"));
    assertFalse(prompt.user().contains("[ACCOUNT_CASH]"));
    assertFalse(prompt.user().contains("[RECENT_TRADES]"));
    assertFalse(prompt.user().contains("[PRICE_HISTORY_"));
    assertGuardrails(prompt.system());
    verifyNoInteractions(trades, snapshots);
    assertDisclaimer(response);
  }

  @Test
  void marketTrendLoadsOnlyRequestedSymbolPricesAndTimestampedHistory() {
    UUID userId = UUID.randomUUID();
    when(market.getCurrent()).thenReturn(new MarketPrices(
        Map.of(
            "BTC", new BigDecimal("61000.00"),
            "ETH", new BigDecimal("3200.00"),
            "SOL", new BigDecimal("155.00")),
        Instant.parse("2026-07-14T10:00:00Z")));
    when(snapshots.findTop20BySymbolOrderByRecordedAtDesc(AssetSymbol.BTC)).thenReturn(List.of(
        new PriceSnapshot(
            AssetSymbol.BTC,
            new BigDecimal("61000.00"),
            Instant.parse("2026-07-14T10:00:00Z")),
        new PriceSnapshot(
            AssetSymbol.BTC,
            new BigDecimal("60750.00"),
            Instant.parse("2026-07-14T09:59:45Z"))));

    ChatService.ChatResponse response = service.query(userId, "BTC'nin son fiyat trendi nasıl?");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.user().contains("[CURRENT_MARKET_PRICES]"));
    assertTrue(prompt.user().contains("BTC priceUsd=61000.00"));
    assertTrue(prompt.user().contains("[PRICE_HISTORY_BTC]"));
    assertTrue(prompt.user().contains("2026-07-14T09:59:45Z | priceUsd=60750.00"));
    assertFalse(prompt.user().contains("ETH priceUsd="));
    assertFalse(prompt.user().contains("SOL priceUsd="));
    assertGuardrails(prompt.system());
    verify(snapshots).findTop20BySymbolOrderByRecordedAtDesc(AssetSymbol.BTC);
    verify(snapshots, never()).findTop20BySymbolOrderByRecordedAtDesc(AssetSymbol.ETH);
    verify(snapshots, never()).findTop20BySymbolOrderByRecordedAtDesc(AssetSymbol.SOL);
    verifyNoInteractions(wallets, assets, trades);
    assertDisclaimer(response);
  }

  @Test
  void coinBalanceLoadsMatchingHoldingWithoutExposingUsdCash() {
    User user = new User("coin-balance@example.com", "hash");
    Wallet wallet = new Wallet(user, new BigDecimal("12500.00"));
    PortfolioAsset btc = new PortfolioAsset(wallet, AssetSymbol.BTC);
    btc.setQuantity(new BigDecimal("0.50000000"));
    when(wallets.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
    when(assets.findByWalletIdOrderBySymbol(wallet.getId())).thenReturn(List.of(btc));
    when(market.getCurrent()).thenReturn(new MarketPrices(
        Map.of("BTC", new BigDecimal("61000.00")),
        Instant.parse("2026-07-14T10:00:00Z")));

    service.query(user.getId(), "BTC bakiyem ne kadar?");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.user().contains("BTC quantity=0.50000000"));
    assertFalse(prompt.user().contains("[ACCOUNT_CASH]"));
    verifyNoInteractions(trades, snapshots);
  }

  @Test
  void genericCryptoBalanceLoadsPortfolioWithoutExposingUsdCash() {
    User user = new User("crypto-balance@example.com", "hash");
    Wallet wallet = new Wallet(user, new BigDecimal("12500.00"));
    PortfolioAsset btc = new PortfolioAsset(wallet, AssetSymbol.BTC);
    btc.setQuantity(new BigDecimal("0.50000000"));
    when(wallets.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
    when(assets.findByWalletIdOrderBySymbol(wallet.getId())).thenReturn(List.of(btc));
    when(market.getCurrent()).thenReturn(new MarketPrices(
        Map.of(
            "BTC", new BigDecimal("61000.00"),
            "ETH", new BigDecimal("3200.00"),
            "SOL", new BigDecimal("155.00")),
        Instant.parse("2026-07-14T10:00:00Z")));

    service.query(user.getId(), "Kripto bakiyemi göster");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.user().contains("[PORTFOLIO_ASSETS]"));
    assertTrue(prompt.user().contains("BTC quantity=0.50000000"));
    assertFalse(prompt.user().contains("[ACCOUNT_CASH]"));
    verifyNoInteractions(trades, snapshots);
  }

  @Test
  void symbolSpecificTradeQuestionFiltersOtherAssetsFromContext() {
    User user = new User("trade-filter@example.com", "hash");
    Wallet wallet = new Wallet(user, new BigDecimal("10000.00"));
    TradeTransaction btcTrade = new TradeTransaction(
        user,
        wallet,
        AssetSymbol.BTC,
        TradeSide.BUY,
        new BigDecimal("0.10000000"),
        new BigDecimal("60000.00"),
        new BigDecimal("6000.00"));
    TradeTransaction ethTrade = new TradeTransaction(
        user,
        wallet,
        AssetSymbol.ETH,
        TradeSide.BUY,
        new BigDecimal("1.00000000"),
        new BigDecimal("3000.00"),
        new BigDecimal("3000.00"));
    when(trades.findTop20ByUserIdOrderByExecutedAtDesc(user.getId()))
        .thenReturn(List.of(btcTrade, ethTrade));

    service.query(user.getId(), "Son BTC işlemlerimi göster");

    Prompt prompt = capturePrompt();
    assertTrue(prompt.user().contains("[RECENT_TRADES]"));
    assertTrue(prompt.user().contains("BTC @ USD 60000.00"));
    assertFalse(prompt.user().contains("ETH @ USD 3000.00"));
    verifyNoInteractions(wallets, assets, snapshots, market);
  }

  private Prompt capturePrompt() {
    ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
    verify(gemini).generate(system.capture(), user.capture());
    return new Prompt(system.getValue(), user.getValue());
  }

  private void assertNoFinancialSections(String prompt) {
    assertTrue(prompt.contains("DATA_CONTEXT\nNONE"));
    assertFalse(prompt.contains("[ACCOUNT_CASH]"));
    assertFalse(prompt.contains("[PORTFOLIO_ASSETS]"));
    assertFalse(prompt.contains("[RECENT_TRADES]"));
    assertFalse(prompt.contains("[CURRENT_MARKET_PRICES]"));
    assertFalse(prompt.contains("[PRICE_HISTORY_"));
  }

  private void assertGuardrails(String system) {
    String normalized = system.replaceAll("\\s+", " ");
    assertTrue(normalized.contains("Never invent missing prices"));
    assertTrue(normalized.contains("support/resistance levels"));
    assertTrue(normalized.contains("must not be presented as a prediction or certainty"));
    assertTrue(normalized.contains("at most three short sentences or bullets"));
  }

  private void assertDisclaimer(ChatService.ChatResponse response) {
    assertEquals(ChatService.DISCLAIMER, response.disclaimer());
    assertTrue(response.answer().endsWith(ChatService.DISCLAIMER));
  }

  private record Prompt(String system, String user) {}
}
