package com.i2i.cryptflow.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.i2i.cryptflow.chat.ChatService;
import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.market.MarketPrices;
import com.i2i.cryptflow.user.User;
import com.i2i.cryptflow.wallet.Wallet;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioServiceTest {
  private final WalletRepository wallets = mock(WalletRepository.class);
  private final PortfolioAssetRepository assets = mock(PortfolioAssetRepository.class);
  private final MarketPriceService market = mock(MarketPriceService.class);
  private final ChatService chatService = mock(ChatService.class);
  private final EquityHistoryRepository equityHistory = mock(EquityHistoryRepository.class);
  private final PortfolioService service = new PortfolioService(
      wallets, assets, market, chatService, equityHistory);

  @Test
  void valuesHoldingsAndCombinesThemWithCash() {
    var user = new User("trader@example.com", "hash");
    var wallet = new Wallet(user, new BigDecimal("1000.00"));
    var bitcoin = new PortfolioAsset(wallet, "BTC");
    bitcoin.setQuantity(new BigDecimal("0.25000000"));
    bitcoin.setAveragePrice(new BigDecimal("50000.00000000"));
    when(wallets.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
    when(assets.findByWalletIdOrderBySymbol(wallet.getId())).thenReturn(List.of(bitcoin));
    when(market.getCurrent()).thenReturn(new MarketPrices(
        Map.of("BTC", new BigDecimal("60000.00")), Instant.now()));

    var result = service.get(user.getId());

    assertEquals(new BigDecimal("15000.00"), result.assetValueUsd());
    assertEquals(new BigDecimal("16000.00"), result.totalValueUsd());
    assertEquals(1, result.assets().size());
    assertEquals(new BigDecimal("15000.00"), result.assets().getFirst().valueUsd());
  }

  @Test
  void cachesSuccessfulAdviceAndRemovesDuplicatedDisclaimer() {
    var userId = UUID.randomUUID();
    String response = "Diversification can reduce concentration risk.\n\n" + ChatService.DISCLAIMER;
    when(chatService.query(org.mockito.ArgumentMatchers.eq(userId), anyString()))
        .thenReturn(new ChatService.ChatResponse(response, ChatService.DISCLAIMER, Instant.now()));

    var first = service.getAiAdvice(userId, "en", false);
    var second = service.getAiAdvice(userId, "EN", false);

    assertEquals("Diversification can reduce concentration risk.", first);
    assertEquals(first, second);
    verify(chatService).query(org.mockito.ArgumentMatchers.eq(userId), anyString());
  }

  @Test
  void returnsPersistedEquityHistoryWithoutRecalculatingPortfolio() {
    var user = new User("trader@example.com", "hash");
    var saved = new EquityHistory(user, new BigDecimal("15125.50"));
    when(equityHistory.findByUserIdOrderByRecordedAtAsc(user.getId())).thenReturn(List.of(saved));

    var result = service.getEquityHistory(user.getId());

    assertEquals(1, result.size());
    assertSame(saved.getRecordedAt(), result.getFirst().time());
    assertEquals(saved.getTotalValue(), result.getFirst().value());
    verifyNoInteractions(wallets, assets, market);
  }

  @Test
  void synthesizesTwoEquityPointsFromCurrentPortfolioWhenHistoryIsEmpty() {
    var user = new User("trader@example.com", "hash");
    var wallet = new Wallet(user, new BigDecimal("1000.00"));
    var ethereum = new PortfolioAsset(wallet, "ETH");
    ethereum.setQuantity(new BigDecimal("2.00000000"));
    when(equityHistory.findByUserIdOrderByRecordedAtAsc(user.getId())).thenReturn(List.of());
    when(wallets.findByUserId(user.getId())).thenReturn(Optional.of(wallet));
    when(assets.findByWalletIdOrderBySymbol(wallet.getId())).thenReturn(List.of(ethereum));
    when(market.getCurrent()).thenReturn(new MarketPrices(
        Map.of("ETH", new BigDecimal("3000.00")), Instant.now()));

    var result = service.getEquityHistory(user.getId());

    assertEquals(2, result.size());
    assertEquals(new BigDecimal("7000.0000000000"), result.getFirst().value());
    assertEquals(result.getFirst().value(), result.getLast().value());
  }
}
