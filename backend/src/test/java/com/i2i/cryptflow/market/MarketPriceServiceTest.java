package com.i2i.cryptflow.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.i2i.cryptflow.shared.model.ExternalPriceProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class MarketPriceServiceTest {
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final ExternalPriceProvider supportedSymbols = mock(ExternalPriceProvider.class);
  private final PriceSnapshotRepository snapshots = mock(PriceSnapshotRepository.class);
  private final MarketPriceService service = new MarketPriceService(redis, supportedSymbols, snapshots);

  @Test
  void returnsRecentHistoryChronologicallyAndNormalizesTheSymbol() {
    var older = new PriceSnapshot("BTC", new BigDecimal("59000.00"), Instant.parse("2026-07-19T10:00:00Z"));
    var newer = new PriceSnapshot("BTC", new BigDecimal("60000.00"), Instant.parse("2026-07-19T11:00:00Z"));
    when(snapshots.findTop40BySymbolOrderByRecordedAtDesc("BTC")).thenReturn(List.of(newer, older));

    var result = service.recentHistory("btc");

    assertEquals(List.of(older, newer), result);
    verify(snapshots).findTop40BySymbolOrderByRecordedAtDesc("BTC");
  }
}
