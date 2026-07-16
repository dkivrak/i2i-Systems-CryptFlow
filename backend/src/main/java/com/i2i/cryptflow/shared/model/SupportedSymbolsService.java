package com.i2i.cryptflow.shared.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fetches all USDT trading pairs from Binance at startup
 * and provides the list of supported symbols to the rest of the application.
 */
@Service
public class SupportedSymbolsService {
  private static final Logger log = LoggerFactory.getLogger(SupportedSymbolsService.class);
  private static final String BINANCE_TICKER_URL = "https://api.binance.com/api/v3/ticker/price";
  private static final String USDT_SUFFIX = "USDT";
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

  private static final List<String> FALLBACK_SYMBOLS = List.of(
      "BTC", "ETH", "SOL", "BNB", "ADA", "XRP", "DOGE", "DOT", "AVAX", "LINK"
  );

  private volatile List<String> symbols = FALLBACK_SYMBOLS;

  @PostConstruct
  public void init() {
    try {
      var client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
      var request = HttpRequest.newBuilder()
          .uri(URI.create(BINANCE_TICKER_URL))
          .timeout(HTTP_TIMEOUT)
          .GET()
          .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        var mapper = new ObjectMapper();
        var root = mapper.readTree(response.body());
        var fetched = new ArrayList<String>();
        for (JsonNode node : root) {
          String pair = node.get("symbol").asText();
          if (pair.endsWith(USDT_SUFFIX)) {
            String coin = pair.substring(0, pair.length() - USDT_SUFFIX.length());
            if (!coin.isEmpty() && !coin.contains("UP") && !coin.contains("DOWN") && !coin.contains("BULL") && !coin.contains("BEAR")) {
              fetched.add(coin);
            }
          }
        }
        if (!fetched.isEmpty()) {
          symbols = Collections.unmodifiableList(fetched);
          log.info("Loaded {} USDT symbols from Binance", symbols.size());
        } else {
          log.warn("No USDT symbols found from Binance, using fallback list");
        }
      } else {
        log.warn("Binance API returned status {}, using fallback symbols", response.statusCode());
      }
    } catch (Exception ex) {
      log.warn("Failed to fetch symbols from Binance, using fallback list: {}", ex.getMessage());
    }
  }

  public List<String> getSymbols() {
    return symbols;
  }

  public boolean isSupported(String symbol) {
    return symbols.contains(symbol);
  }
}
