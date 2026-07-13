package com.i2i.cryptflow.wallet;

import com.i2i.cryptflow.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="wallets")
public class Wallet {
  @Id private UUID id;
  @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id", unique=true) private User user;
  @Column(name="usd_balance", nullable=false, precision=19, scale=2) private BigDecimal usdBalance;
  @Column(name="created_at", nullable=false) private Instant createdAt;
  @Column(name="updated_at", nullable=false) private Instant updatedAt;
  protected Wallet() {}
  public Wallet(User user, BigDecimal balance){id=UUID.randomUUID();this.user=user;usdBalance=balance;createdAt=Instant.now();updatedAt=createdAt;}
  public UUID getId(){return id;} public User getUser(){return user;} public BigDecimal getUsdBalance(){return usdBalance;}
  public void setUsdBalance(BigDecimal value){usdBalance=value;updatedAt=Instant.now();}
}

