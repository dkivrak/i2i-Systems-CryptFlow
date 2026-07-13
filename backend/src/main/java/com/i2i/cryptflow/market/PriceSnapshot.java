package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.model.AssetSymbol;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="price_snapshots")
public class PriceSnapshot {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private AssetSymbol symbol;
  @Column(name="price_usd", nullable=false, precision=19, scale=2) private BigDecimal priceUsd;
  @Column(name="recorded_at", nullable=false) private Instant recordedAt;
  protected PriceSnapshot() {}
  public PriceSnapshot(AssetSymbol symbol, BigDecimal price, Instant time){this.symbol=symbol;priceUsd=price;recordedAt=time;}
  public AssetSymbol getSymbol(){return symbol;} public BigDecimal getPriceUsd(){return priceUsd;} public Instant getRecordedAt(){return recordedAt;}
}

