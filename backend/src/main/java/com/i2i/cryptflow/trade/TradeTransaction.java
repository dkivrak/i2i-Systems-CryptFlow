package com.i2i.cryptflow.trade;

import com.i2i.cryptflow.user.User;
import com.i2i.cryptflow.wallet.Wallet;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="trade_transactions")
public class TradeTransaction {
  @Id private UUID id;
  @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id") private User user;
  @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="wallet_id") private Wallet wallet;
  @Column(nullable=false, length=10) private String symbol;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private TradeSide side;
  @Column(nullable=false, precision=28, scale=8) private BigDecimal quantity;
  @Column(name="unit_price_usd", nullable=false, precision=19, scale=2) private BigDecimal unitPriceUsd;
  @Column(name="total_usd", nullable=false, precision=19, scale=2) private BigDecimal totalUsd;
  @Column(name="executed_at", nullable=false) private Instant executedAt;

  protected TradeTransaction() {}

  public TradeTransaction(User user, Wallet wallet, String symbol, TradeSide side, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {
    id = UUID.randomUUID();
    this.user = user;
    this.wallet = wallet;
    this.symbol = symbol;
    this.side = side;
    this.quantity = quantity;
    unitPriceUsd = unitPrice;
    totalUsd = total;
    executedAt = Instant.now();
  }

  public UUID getId() { return id; }

  public String getSymbol() { return symbol; }

  public TradeSide getSide() { return side; }

  public BigDecimal getQuantity() { return quantity; }

  public BigDecimal getUnitPriceUsd() { return unitPriceUsd; }

  public BigDecimal getTotalUsd() { return totalUsd; }

  public Instant getExecutedAt() { return executedAt; }
}
