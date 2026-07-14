package com.i2i.cryptflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.shared.model.AssetSymbol;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class PriceWebSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper objectMapper;
  private final MarketPriceService marketPriceService;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final CopyOnWriteArraySet<WebSocketSession> activeSessions = new CopyOnWriteArraySet<>();
  private final ConcurrentHashMap<AssetSymbol, WebSocket> binanceConnections = new ConcurrentHashMap<>();

  public PriceWebSocketHandler(ObjectMapper objectMapper, MarketPriceService marketPriceService) {
    this.objectMapper = objectMapper;
    this.marketPriceService = marketPriceService;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    activeSessions.add(session);
    ensureBinanceConnection(AssetSymbol.BTC);
    ensureBinanceConnection(AssetSymbol.ETH);
    ensureBinanceConnection(AssetSymbol.SOL);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    activeSessions.remove(session);
    if (activeSessions.isEmpty()) {
      closeBinanceConnection(AssetSymbol.BTC);
      closeBinanceConnection(AssetSymbol.ETH);
      closeBinanceConnection(AssetSymbol.SOL);
    }
  }

  private void ensureBinanceConnection(AssetSymbol symbol) {
    binanceConnections.compute(symbol, (key, existing) -> {
      if (existing != null && !existing.isInputClosed() && !existing.isOutputClosed()) {
        return existing;
      }
      return openBinanceConnection(key);
    });
  }

  private WebSocket openBinanceConnection(AssetSymbol symbol) {
    try {
      URI uri;
      if (symbol == AssetSymbol.BTC) {
        uri = URI.create("wss://stream.binance.com/ws/btcusdt@trade");
      } else if (symbol == AssetSymbol.ETH) {
        uri = URI.create("wss://stream.binance.com/ws/ethusdt@trade");
      } else {
        uri = URI.create("wss://stream.binance.com/ws/solusdt@trade");
      }
      return httpClient.newWebSocketBuilder().buildAsync(uri, new BinanceListener(symbol)).join();
    } catch (Exception exception) {
      System.err.println("Binance WebSocket bağlantısı açılamadı (" + symbol + "): " + exception.getMessage());
      return null;
    }
  }

  private void closeBinanceConnection(AssetSymbol symbol) {
    WebSocket webSocket = binanceConnections.remove(symbol);
    if (webSocket == null || webSocket.isOutputClosed()) {
      return;
    }

    webSocket.sendClose(1000, "No active React sessions");
  }

  private void broadcastPrice(AssetSymbol symbol, String binanceMessage) {
    String price = extractPrice(binanceMessage);
    if (price == null || price.isBlank()) {
      return;
    }

    marketPriceService.updateSinglePrice(symbol, new BigDecimal(price), Instant.now());

    String rawPayload;
    try {
      rawPayload = objectMapper.writeValueAsString(Map.of("s", symbol.name(), "p", price));
    } catch (Exception exception) {
      System.err.println("React oturumuna fiyat gönderilemedi (" + symbol + "): " + exception.getMessage());
      return;
    }

    System.out.println("⚡ [BINANCE -> REACT] " + symbol.name() + " fiyatı gönderildi: " + rawPayload);

    for (WebSocketSession session : activeSessions) {
      if (!session.isOpen()) {
        activeSessions.remove(session);
        continue;
      }

      try {
        session.sendMessage(new TextMessage(rawPayload));
      } catch (Exception exception) {
        System.err.println("React oturumuna fiyat gönderilemedi (" + symbol + "): " + exception.getMessage());
      }
    }
  }

  private String extractPrice(String binanceMessage) {
    try {
      return objectMapper.readTree(binanceMessage).path("p").asText(null);
    } catch (Exception exception) {
      System.err.println("Binance mesajı ayrıştırılamadı: " + exception.getMessage());
      return null;
    }
  }

  private final class BinanceListener implements WebSocket.Listener {
    private final AssetSymbol symbol;
    private final StringBuilder buffer = new StringBuilder();

    private BinanceListener(AssetSymbol symbol) {
      this.symbol = symbol;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
      System.out.println("Binance WebSocket bağlandı: " + symbol);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      buffer.append(data);
      if (last) {
        String message = buffer.toString();
        buffer.setLength(0);
        broadcastPrice(symbol, message);
      }
      webSocket.request(1);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      System.err.println("Binance WebSocket hatası (" + symbol + "): " + error.getMessage());
      binanceConnections.remove(symbol, webSocket);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      System.out.println("Binance WebSocket kapandı (" + symbol + "): " + statusCode + " - " + reason);
      binanceConnections.remove(symbol, webSocket);
      return CompletableFuture.completedFuture(null);
    }
  }
}