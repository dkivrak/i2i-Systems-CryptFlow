package com.i2i.cryptflow.market;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController @RequestMapping("/api/market")
public class MarketController {
  private final MarketPriceService market;
  private final PriceSnapshotRepository snapshots;

  public MarketController(MarketPriceService market, PriceSnapshotRepository snapshots) {
    this.market = market;
    this.snapshots = snapshots;
  }

  @GetMapping("/prices") MarketPrices prices(){return market.getCurrent();}

  @GetMapping("/history/{symbol}")
  List<PriceSnapshot> history(@PathVariable String symbol) {
    var list = new ArrayList<>(snapshots.findTop40BySymbolOrderByRecordedAtDesc(symbol.toUpperCase()));
    Collections.reverse(list);
    return list;
  }
}

