package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.model.AssetSymbol;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TickerEngine {
  private final MarketPriceService market; private final PriceSnapshotRepository snapshots; private final PriceSnapshotWriter writer; private final SimpMessagingTemplate messaging;
  private final BigDecimal maxChange; private final Map<AssetSymbol,BigDecimal> initial;
  public TickerEngine(MarketPriceService market,PriceSnapshotRepository snapshots,PriceSnapshotWriter writer,SimpMessagingTemplate messaging,
      @Value("${app.ticker.max-change-percent}") BigDecimal maxChange,
      @Value("${app.ticker.initial-prices.BTC}") BigDecimal btc,@Value("${app.ticker.initial-prices.ETH}") BigDecimal eth,@Value("${app.ticker.initial-prices.SOL}") BigDecimal sol){
    this.market=market;this.snapshots=snapshots;this.writer=writer;this.messaging=messaging;this.maxChange=maxChange;initial=Map.of(AssetSymbol.BTC,btc,AssetSymbol.ETH,eth,AssetSymbol.SOL,sol);
  }
  @EventListener(ApplicationReadyEvent.class)
  public void bootstrap(){
    try{market.getCurrent();return;}catch(Exception ignored){}
    var prices=new EnumMap<AssetSymbol,BigDecimal>(AssetSymbol.class);boolean foundAll=true;
    for(var s:AssetSymbol.values()){
      var latest=snapshots.findFirstBySymbolOrderByRecordedAtDesc(s);
      if(latest.isEmpty()){foundAll=false;break;}prices.put(s,latest.get().getPriceUsd());
    }
    if(!foundAll){prices.clear();prices.putAll(initial);var now=Instant.now();writer.write(prices,now);market.overwrite(prices,now);}
    else market.overwrite(prices,Instant.now());
  }
  @Scheduled(fixedDelayString="${app.ticker.interval-ms}")
  public void tick(){
    MarketPrices current;try{current=market.getCurrent();}catch(Exception ex){bootstrap();return;}
    var next=new EnumMap<AssetSymbol,BigDecimal>(AssetSymbol.class);
    for(var s:AssetSymbol.values()){
      double delta=ThreadLocalRandom.current().nextDouble(maxChange.negate().doubleValue(),maxChange.doubleValue());
      var factor=BigDecimal.ONE.add(BigDecimal.valueOf(delta).movePointLeft(2));
      next.put(s,current.prices().get(s.name()).multiply(factor).max(new BigDecimal("0.01")).setScale(2,RoundingMode.HALF_UP));
    }
    var now=Instant.now();writer.write(next,now);market.overwrite(next,now);messaging.convertAndSend("/topic/market/prices",market.getCurrent());
  }
}

