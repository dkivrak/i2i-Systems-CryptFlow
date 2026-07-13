package com.i2i.cryptflow.trade;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.portfolio.*;
import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.AssetSymbol;
import com.i2i.cryptflow.user.User;
import com.i2i.cryptflow.wallet.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;

class TradeServiceTest {
  WalletRepository wallets=mock(WalletRepository.class);PortfolioAssetRepository assets=mock(PortfolioAssetRepository.class);
  TradeTransactionRepository trades=mock(TradeTransactionRepository.class);MarketPriceService market=mock(MarketPriceService.class);
  TradeService service=new TradeService(wallets,assets,trades,market);

  @Test void rejectsMoreThanEightDecimals(){
    var ex=assertThrows(ApiException.class,()->service.execute(UUID.randomUUID(),AssetSymbol.BTC,TradeSide.BUY,new BigDecimal("0.000000001")));
    assertEquals("INVALID_AMOUNT",ex.getCode());
  }

  @Test void rejectsInsufficientFunds(){
    var user=new User("test@example.com","hash");var wallet=new Wallet(user,new BigDecimal("10.00"));var asset=new PortfolioAsset(wallet,AssetSymbol.BTC);
    when(wallets.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(wallet));when(assets.findForUpdate(wallet.getId(),AssetSymbol.BTC)).thenReturn(Optional.of(asset));when(market.price(AssetSymbol.BTC)).thenReturn(new BigDecimal("60000.00"));
    var ex=assertThrows(ApiException.class,()->service.execute(user.getId(),AssetSymbol.BTC,TradeSide.BUY,new BigDecimal("1")));
    assertEquals("INSUFFICIENT_FUNDS",ex.getCode());verify(trades,never()).save(any());
  }
}

