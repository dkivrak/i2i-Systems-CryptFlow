package com.i2i.cryptflow.market;

import com.i2i.cryptflow.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_alerts")
public class PriceAlert {
  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false, length = 10)
  private String symbol;

  @Column(name = "target_price", nullable = false, precision = 28, scale = 8)
  private BigDecimal targetPrice;

  @Column(nullable = false, length = 5)
  private String condition; // ABOVE, BELOW

  @Column(name = "is_triggered", nullable = false)
  private boolean isTriggered;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PriceAlert() {}

  public PriceAlert(User user, String symbol, BigDecimal targetPrice, String condition) {
    this.id = UUID.randomUUID();
    this.user = user;
    this.symbol = symbol;
    this.targetPrice = targetPrice;
    this.condition = condition;
    this.isTriggered = false;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public User getUser() { return user; }
  public String getSymbol() { return symbol; }
  public BigDecimal getTargetPrice() { return targetPrice; }
  public String getCondition() { return condition; }
  public boolean isTriggered() { return isTriggered; }
  public void setTriggered(boolean triggered) { isTriggered = triggered; }
  public Instant getCreatedAt() { return createdAt; }
}
