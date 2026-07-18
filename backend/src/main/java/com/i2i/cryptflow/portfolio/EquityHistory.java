package com.i2i.cryptflow.portfolio;

import com.i2i.cryptflow.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "equity_history")
public class EquityHistory {
  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "total_value", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalValue;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  protected EquityHistory() {}

  public EquityHistory(User user, BigDecimal totalValue) {
    this.id = UUID.randomUUID();
    this.user = user;
    this.totalValue = totalValue;
    this.recordedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public User getUser() { return user; }
  public BigDecimal getTotalValue() { return totalValue; }
  public Instant getRecordedAt() { return recordedAt; }
}
