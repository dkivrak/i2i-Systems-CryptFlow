package com.i2i.cryptflow.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.ExternalPriceProvider;
import com.i2i.cryptflow.user.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertServiceTest {
  private final PriceAlertRepository alerts = mock(PriceAlertRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final ExternalPriceProvider supportedSymbols = mock(ExternalPriceProvider.class);
  private final AlertService service = new AlertService(alerts, users, supportedSymbols);

  @BeforeEach
  void setUp() {
    when(supportedSymbols.isSupported("BTC")).thenReturn(true);
  }

  @Test
  void rejectsUnsupportedSymbolBeforePersistenceLookups() {
    assertCode("UNSUPPORTED_SYMBOL", "NOTACOIN", BigDecimal.ONE, "ABOVE");
    verifyNoInteractions(alerts, users);
  }

  @Test
  void rejectsInvalidConditionBeforePersistenceLookups() {
    assertCode("INVALID_CONDITION", "BTC", BigDecimal.ONE, "SIDEWAYS");
    verifyNoInteractions(alerts, users);
  }

  @Test
  void rejectsNonPositiveTargetBeforePersistenceLookups() {
    assertCode("INVALID_TARGET_PRICE", "BTC", BigDecimal.ZERO, "ABOVE");
    verifyNoInteractions(alerts, users);
  }

  private void assertCode(String expected, String symbol, BigDecimal targetPrice, String condition) {
    ApiException exception = assertThrows(
        ApiException.class,
        () -> service.create(UUID.randomUUID(), symbol, targetPrice, condition));
    assertEquals(expected, exception.getCode());
  }
}
