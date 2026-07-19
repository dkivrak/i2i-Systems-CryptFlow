package com.i2i.cryptflow.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.i2i.cryptflow.portfolio.PortfolioAssetRepository;
import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.ExternalPriceProvider;
import com.i2i.cryptflow.user.UserRepository;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
  private final LimitOrderRepository orders = mock(LimitOrderRepository.class);
  private final WalletRepository wallets = mock(WalletRepository.class);
  private final PortfolioAssetRepository assets = mock(PortfolioAssetRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final TradeService tradeService = mock(TradeService.class);
  private final ExternalPriceProvider supportedSymbols = mock(ExternalPriceProvider.class);
  private final OrderService service = new OrderService(
      orders, wallets, assets, users, tradeService, supportedSymbols);

  @BeforeEach
  void setUp() {
    when(supportedSymbols.isSupported("BTC")).thenReturn(true);
  }

  @Test
  void rejectsUnsupportedSymbolBeforePersistenceLookups() {
    assertCode("UNSUPPORTED_SYMBOL", () -> place("NOTACOIN", "BUY", "LIMIT"));
    verifyNoInteractions(users, wallets, orders, assets);
  }

  @Test
  void rejectsInvalidSideBeforePersistenceLookups() {
    assertCode("INVALID_ORDER_SIDE", () -> place("BTC", "SIDEWAYS", "LIMIT"));
    verifyNoInteractions(users, wallets, orders, assets);
  }

  @Test
  void rejectsInvalidTypeBeforePersistenceLookups() {
    assertCode("INVALID_ORDER_TYPE", () -> place("BTC", "BUY", "MAGIC"));
    verifyNoInteractions(users, wallets, orders, assets);
  }

  @Test
  void rejectsBuyStopLossBeforePersistenceLookups() {
    assertCode("INVALID_ORDER_COMBINATION", () -> place("BTC", "BUY", "STOP_LOSS"));
    verifyNoInteractions(users, wallets, orders, assets);
  }

  private void place(String symbol, String side, String type) {
    service.place(UUID.randomUUID(), symbol, side, type, BigDecimal.ONE, BigDecimal.ONE);
  }

  private void assertCode(String expected, Runnable action) {
    ApiException exception = assertThrows(ApiException.class, action::run);
    assertEquals(expected, exception.getCode());
  }
}
