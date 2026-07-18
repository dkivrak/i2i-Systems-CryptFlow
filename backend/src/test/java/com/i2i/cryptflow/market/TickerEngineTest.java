package com.i2i.cryptflow.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.SupportedSymbolsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import com.i2i.cryptflow.trade.OrderService;
import com.i2i.cryptflow.market.AlertService;

@SuppressWarnings("unchecked")
class TickerEngineTest {
  private final MarketPriceService market = mock(MarketPriceService.class);
  private final PriceSnapshotRepository snapshots = mock(PriceSnapshotRepository.class);
  private final PriceSnapshotWriter writer = mock(PriceSnapshotWriter.class);
  private final SupportedSymbolsService supportedSymbols = mock(SupportedSymbolsService.class);
  private final OrderService orderService = mock(OrderService.class);
  private final AlertService alertService = mock(AlertService.class);
  private final TickerEngine engine = new TickerEngine(
      market,
      snapshots,
      writer,
      supportedSymbols,
      orderService,
      alertService,
      new BigDecimal("60000.00"),
      new BigDecimal("3000.00"),
      new BigDecimal("150.00")
  );

  @BeforeEach
  void setUp() {
    when(supportedSymbols.getSymbols()).thenReturn(List.of("BTC", "ETH", "SOL"));
  }

  @Test
  void bootstrapsCleanRedisFromSnapshotsAndInitialPrices() {
    when(market.getCurrent()).thenThrow(new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "MARKET_DATA_UNAVAILABLE",
        "unavailable"
    ));
    when(snapshots.findFirstBySymbolOrderByRecordedAtDesc("BTC"))
        .thenReturn(Optional.of(new PriceSnapshot(
            "BTC",
            new BigDecimal("65000.00"),
            Instant.parse("2026-01-01T00:00:00Z")
        )));
    when(snapshots.findFirstBySymbolOrderByRecordedAtDesc("ETH"))
        .thenReturn(Optional.empty());
    when(snapshots.findFirstBySymbolOrderByRecordedAtDesc("SOL"))
        .thenReturn(Optional.empty());

    engine.bootstrap();

    var prices = capturePricesFromOverwrite();
    assertEquals(new BigDecimal("65000.00"), prices.get("BTC"));
    assertEquals(new BigDecimal("3000.00"), prices.get("ETH"));
    assertEquals(new BigDecimal("150.00"), prices.get("SOL"));
    verify(writer).write(any(), any());
  }

  @Test
  void snapshotsCurrentPricesWithoutOverwritingThem() {
    var current = Map.of(
        "BTC", new BigDecimal("70000.00"),
        "ETH", new BigDecimal("4000.00"),
        "SOL", new BigDecimal("200.00")
    );
    when(market.getCurrent()).thenReturn(new MarketPrices(current, Instant.now()));

    engine.tick();

    var pricesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(writer).write(pricesCaptor.capture(), any());
    assertEquals(current.get("BTC"), pricesCaptor.getValue().get("BTC"));
    assertEquals(current.get("ETH"), pricesCaptor.getValue().get("ETH"));
    assertEquals(current.get("SOL"), pricesCaptor.getValue().get("SOL"));
    verify(market, never()).overwrite(any(), any());
  }

  private Map<String, BigDecimal> capturePricesFromOverwrite() {
    var pricesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(market).overwrite(pricesCaptor.capture(), any());
    return pricesCaptor.getValue();
  }
}
