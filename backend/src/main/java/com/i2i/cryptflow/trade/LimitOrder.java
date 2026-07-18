package com.i2i.cryptflow.trade;

import com.i2i.cryptflow.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "limit_orders")
public class LimitOrder {
  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false, length = 10)
  private String symbol;

  @Column(nullable = false, length = 4)
  private String side; // BUY, SELL

  @Column(nullable = false, length = 10)
  private String type; // LIMIT, STOP_LOSS

  @Column(name = "target_price", nullable = false, precision = 28, scale = 8)
  private BigDecimal targetPrice;

  @Column(nullable = false, precision = 28, scale = 8)
  private BigDecimal quantity;

  @Column(nullable = false, length = 10)
  private String status; // PENDING, EXECUTED, CANCELLED

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected LimitOrder() {}

  public LimitOrder(User user, String symbol, String side, String type, BigDecimal targetPrice, BigDecimal quantity) {
    this.id = UUID.randomUUID();
    this.user = user;
    this.symbol = symbol;
    this.side = side;
    this.type = type;
    this.targetPrice = targetPrice;
    this.quantity = quantity;
    this.status = "PENDING";
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public User getUser() { return user; }
  public String getSymbol() { return symbol; }
  public String getSide() { return side; }
  public String getType() { return type; }
  public BigDecimal getTargetPrice() { return targetPrice; }
  public BigDecimal getQuantity() { return quantity; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
}
