package com.i2i.cryptflow.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceSnapshotWriter {
  private final PriceSnapshotRepository snapshots;
  public PriceSnapshotWriter(PriceSnapshotRepository snapshots){this.snapshots=snapshots;}
  @Transactional public void write(Map<String,BigDecimal> prices,Instant time){
    snapshots.saveAll(prices.entrySet().stream().map(e->new PriceSnapshot(e.getKey(),e.getValue(),time)).toList());
  }
}
