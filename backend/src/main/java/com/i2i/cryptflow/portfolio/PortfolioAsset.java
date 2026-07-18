package com.i2i.cryptflow.portfolio;

import com.i2i.cryptflow.wallet.Wallet;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="portfolio_assets", uniqueConstraints=@UniqueConstraint(name="uk_portfolio_wallet_symbol", columnNames={"wallet_id","symbol"}))
public class PortfolioAsset {
  @Id private UUID id;
  @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="wallet_id") private Wallet wallet;
  @Column(nullable=false, length=10) private String symbol;
  @Column(nullable=false, precision=28, scale=8) private BigDecimal quantity;
  @Column(name="average_price", nullable=false, precision=28, scale=8) private BigDecimal averagePrice;
  @Column(name="created_at", nullable=false) private Instant createdAt;
  @Column(name="updated_at", nullable=false) private Instant updatedAt;
  protected PortfolioAsset() {}
  public PortfolioAsset(Wallet wallet, String symbol){id=UUID.randomUUID();this.wallet=wallet;this.symbol=symbol;quantity=BigDecimal.ZERO.setScale(8);averagePrice=BigDecimal.ZERO.setScale(8);createdAt=Instant.now();updatedAt=createdAt;}
  public String getSymbol(){return symbol;} public BigDecimal getQuantity(){return quantity;}
  public void setQuantity(BigDecimal value){quantity=value;updatedAt=Instant.now();}
  public BigDecimal getAveragePrice(){return averagePrice;}
  public void setAveragePrice(BigDecimal value){averagePrice=value;updatedAt=Instant.now();}
}
