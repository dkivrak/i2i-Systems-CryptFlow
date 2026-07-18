package com.i2i.cryptflow.shared.model;

import java.math.BigDecimal;
import java.util.List;

public interface ExternalPriceProvider {
  List<String> getSymbols();
  boolean isSupported(String symbol);
  BigDecimal getInitialPrice(String symbol);
}
