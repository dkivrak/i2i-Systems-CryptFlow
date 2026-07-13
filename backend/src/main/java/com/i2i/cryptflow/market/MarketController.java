package com.i2i.cryptflow.market;

import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/market")
public class MarketController {
  private final MarketPriceService market;
  public MarketController(MarketPriceService market){this.market=market;}
  @GetMapping("/prices") MarketPrices prices(){return market.getCurrent();}
}

