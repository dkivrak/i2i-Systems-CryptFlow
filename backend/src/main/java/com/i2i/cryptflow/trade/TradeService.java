package com.i2i.cryptflow.trade;

import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.portfolio.PortfolioAssetRepository;
import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.AssetSymbol;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeService {
  private final WalletRepository wallets;private final PortfolioAssetRepository assets;private final TradeTransactionRepository trades;private final MarketPriceService market;
  public TradeService(WalletRepository w,PortfolioAssetRepository a,TradeTransactionRepository t,MarketPriceService m){wallets=w;assets=a;trades=t;market=m;}
  @Transactional public TradeResult execute(UUID userId,AssetSymbol symbol,TradeSide side,BigDecimal rawQuantity){
    if(rawQuantity==null||rawQuantity.signum()<=0||Math.max(0,rawQuantity.stripTrailingZeros().scale())>8)throw invalidAmount();
    var quantity=rawQuantity.setScale(8,RoundingMode.UNNECESSARY);
    var wallet=wallets.findByUserIdForUpdate(userId).orElseThrow();
    var asset=assets.findForUpdate(wallet.getId(),symbol).orElseThrow();
    var price=market.price(symbol).setScale(2,RoundingMode.HALF_UP);
    var total=quantity.multiply(price).setScale(2,RoundingMode.HALF_UP);
    if(total.signum()<=0)throw invalidAmount();
    if(side==TradeSide.BUY){
      if(wallet.getUsdBalance().compareTo(total)<0)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INSUFFICIENT_FUNDS","Bu işlem için yeterli USD bakiyeniz yok.");
      wallet.setUsdBalance(wallet.getUsdBalance().subtract(total));asset.setQuantity(asset.getQuantity().add(quantity));
    }else{
      if(asset.getQuantity().compareTo(quantity)<0)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INSUFFICIENT_ASSET_BALANCE","Satış için yeterli varlık bakiyeniz yok.");
      asset.setQuantity(asset.getQuantity().subtract(quantity));wallet.setUsdBalance(wallet.getUsdBalance().add(total));
    }
    var trade=trades.save(new TradeTransaction(wallet.getUser(),wallet,symbol,side,quantity,price,total));
    return from(trade);
  }
  @Transactional(readOnly=true) public Page<TradeResult> history(UUID userId,int page,int size){
    return trades.findByUserIdOrderByExecutedAtDesc(userId,PageRequest.of(Math.max(0,page),Math.min(Math.max(1,size),100))).map(this::from);
  }
  private TradeResult from(TradeTransaction t){return new TradeResult(t.getId(),t.getSymbol(),t.getSide(),t.getQuantity(),t.getUnitPriceUsd(),t.getTotalUsd(),t.getExecutedAt());}
  private ApiException invalidAmount(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_AMOUNT","Miktar pozitif ve en fazla 8 ondalıklı olmalıdır.");}
  public record TradeResult(UUID id,AssetSymbol symbol,TradeSide side,BigDecimal quantity,BigDecimal unitPriceUsd,BigDecimal totalUsd,Instant executedAt){}
}

