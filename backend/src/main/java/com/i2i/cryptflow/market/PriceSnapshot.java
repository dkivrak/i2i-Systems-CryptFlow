package com.i2i.cryptflow.market;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="price_snapshots")
public class PriceSnapshot {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false, length=10) private String symbol;
  @Column(name="price_usd", nullable=false, precision=28, scale=8) private BigDecimal priceUsd;
  @Column(name="recorded_at", nullable=false) private Instant recordedAt;
  protected PriceSnapshot() {}
  public PriceSnapshot(String symbol, BigDecimal price, Instant time){this.symbol=symbol;priceUsd=price;recordedAt=time;}
  public String getSymbol() {
    return symbol;
  }

  public BigDecimal getPriceUsd() {
    return priceUsd;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }
}
