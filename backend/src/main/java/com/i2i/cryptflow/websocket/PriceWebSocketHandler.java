package com.i2i.cryptflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.i2i.cryptflow.market.MarketPriceService;

import com.i2i.cryptflow.shared.model.SupportedSymbolsService;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class PriceWebSocketHandler extends TextWebSocketHandler {
  private static final Logger log = LoggerFactory.getLogger(PriceWebSocketHandler.class);
  private static final int SEND_TIME_LIMIT_MS = 10_000;
  private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;
  private static final long CONNECT_TIMEOUT_MS = 10_000;
  private static final long INITIAL_RETRY_DELAY_MS = 1_000;
  private static final long MAX_RETRY_DELAY_MS = 30_000;

  private final ObjectMapper objectMapper;
  private final MarketPriceService marketPriceService;
  private final BinanceConnector binanceConnector;
  private final ScheduledExecutorService reconnectExecutor;
  private final SupportedSymbolsService supportedSymbols;
  private final long connectTimeoutMs;
  private final long initialRetryDelayMs;
  private final long maxRetryDelayMs;
  private final boolean ownsReconnectExecutor;
  private final Object lifecycleMonitor = new Object();
  private final ConcurrentHashMap<String, ConcurrentWebSocketSessionDecorator> activeSessions =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, BinanceConnection> binanceConnections =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ConnectionAttempt> connectionAttempts =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, RetryTask> reconnectTasks =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Integer> retryCounts =
      new ConcurrentHashMap<>();

  private long lifecycleGeneration;
  private boolean shuttingDown;

  @Autowired
  public PriceWebSocketHandler(
      ObjectMapper objectMapper,
      MarketPriceService marketPriceService,
      SupportedSymbolsService supportedSymbols
  ) {
    this(
        objectMapper,
        marketPriceService,
        supportedSymbols,
        defaultConnector(supportedSymbols, CONNECT_TIMEOUT_MS),
        newReconnectExecutor(),
        CONNECT_TIMEOUT_MS,
        INITIAL_RETRY_DELAY_MS,
        MAX_RETRY_DELAY_MS,
        true
    );
  }

  PriceWebSocketHandler(
      ObjectMapper objectMapper,
      MarketPriceService marketPriceService,
      SupportedSymbolsService supportedSymbols,
      BinanceConnector binanceConnector,
      ScheduledExecutorService reconnectExecutor,
      long connectTimeoutMs,
      long initialRetryDelayMs,
      long maxRetryDelayMs
  ) {
    this(
        objectMapper,
        marketPriceService,
        supportedSymbols,
        binanceConnector,
        reconnectExecutor,
        connectTimeoutMs,
        initialRetryDelayMs,
        maxRetryDelayMs,
        false
    );
  }

  private PriceWebSocketHandler(
      ObjectMapper objectMapper,
      MarketPriceService marketPriceService,
      SupportedSymbolsService supportedSymbols,
      BinanceConnector binanceConnector,
      ScheduledExecutorService reconnectExecutor,
      long connectTimeoutMs,
      long initialRetryDelayMs,
      long maxRetryDelayMs,
      boolean ownsReconnectExecutor
  ) {
    if (connectTimeoutMs <= 0 || initialRetryDelayMs <= 0 || maxRetryDelayMs <= 0) {
      throw new IllegalArgumentException("WebSocket timeout and retry delays must be positive");
    }

    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.marketPriceService = Objects.requireNonNull(marketPriceService);
    this.supportedSymbols = Objects.requireNonNull(supportedSymbols);
    this.binanceConnector = Objects.requireNonNull(binanceConnector);
    this.reconnectExecutor = Objects.requireNonNull(reconnectExecutor);
    this.connectTimeoutMs = connectTimeoutMs;
    this.initialRetryDelayMs = Math.min(initialRetryDelayMs, maxRetryDelayMs);
    this.maxRetryDelayMs = maxRetryDelayMs;
    this.ownsReconnectExecutor = ownsReconnectExecutor;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    var concurrentSession = new ConcurrentWebSocketSessionDecorator(
        session,
        SEND_TIME_LIMIT_MS,
        BUFFER_SIZE_LIMIT_BYTES,
        ConcurrentWebSocketSessionDecorator.OverflowStrategy.DROP
    );

    long generation;
    ConcurrentWebSocketSessionDecorator replacedSession;
    synchronized (lifecycleMonitor) {
      if (shuttingDown) {
        closeReactSession(concurrentSession, CloseStatus.GOING_AWAY);
        return;
      }
      replacedSession = activeSessions.put(session.getId(), concurrentSession);
      generation = lifecycleGeneration;
    }

    if (replacedSession != null && replacedSession != concurrentSession) {
      closeReactSession(replacedSession, CloseStatus.NORMAL);
    }
    List<List<String>> groups = getSymbolGroups(supportedSymbols);
    for (int i = 0; i < groups.size(); i++) {
      ensureBinanceConnection("group-" + i, generation);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    removeReactSession(session.getId(), null);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    ConcurrentWebSocketSessionDecorator concurrentSession = activeSessions.get(session.getId());
    removeReactSession(session.getId(), concurrentSession);
    closeReactSession(concurrentSession, CloseStatus.SERVER_ERROR);
  }

  private void ensureBinanceConnection(String symbol, long generation) {
    ConnectionAttempt attempt = null;
    BinanceConnection staleConnection = null;
    synchronized (lifecycleMonitor) {
      if (!isConnectionDesiredLocked(generation)) {
        return;
      }

      BinanceConnection existing = binanceConnections.get(symbol);
      if (isUsable(existing)) {
        return;
      }
      if (existing != null) {
        binanceConnections.remove(symbol, existing);
        existing.attempt.intentionalClose.set(true);
        staleConnection = existing;
      }
      if (!connectionAttempts.containsKey(symbol) && !reconnectTasks.containsKey(symbol)) {
        attempt = new ConnectionAttempt(generation);
        connectionAttempts.put(symbol, attempt);
      }
    }

    if (staleConnection != null) {
      closeAttemptSocket(
          staleConnection.attempt,
          staleConnection.webSocket,
          "Replacing stale connection"
      );
    }
    if (attempt == null) {
      return;
    }
    ConnectionAttempt connectionAttempt = attempt;

    try {
      var listener = new BinanceListener(symbol, connectionAttempt);
      CompletableFuture<WebSocket> future = Objects.requireNonNull(
          binanceConnector.connect(symbol, listener),
          "Binance connector returned null"
      );
      CompletableFuture<WebSocket> timedFuture =
          future.orTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS);
      connectionAttempt.attach(timedFuture);
      timedFuture.whenComplete(
          (webSocket, error) ->
              completeConnectionAttempt(symbol, connectionAttempt, webSocket, error)
      );
    } catch (Exception exception) {
      completeConnectionAttempt(symbol, connectionAttempt, null, exception);
    }
  }

  private void completeConnectionAttempt(
      String symbol,
      ConnectionAttempt attempt,
      WebSocket webSocket,
      Throwable error
  ) {
    connectionAttempts.remove(symbol, attempt);

    if (attempt.cancelled.get() || attempt.intentionalClose.get()) {
      if (webSocket != null) {
        closeAttemptSocket(attempt, webSocket, "Inactive connection attempt");
      }
      return;
    }
    if (error != null || webSocket == null) {
      attempt.cancel();
      if (webSocket != null) {
        closeAttemptSocket(attempt, webSocket, "Failed connection attempt");
      }
      log.warn("Binance WebSocket connection failed for {}: {}", symbol, errorMessage(error));
      scheduleReconnect(symbol, attempt.generation);
      return;
    }

    if (attempt.opened.compareAndSet(false, true)) {
      handleBinanceOpen(symbol, attempt, webSocket);
    }

    if (!isCurrentConnection(symbol, webSocket) && !attempt.intentionalClose.get()) {
      scheduleReconnect(symbol, attempt.generation);
    }
  }

  private void handleBinanceOpen(
      String symbol,
      ConnectionAttempt attempt,
      WebSocket webSocket
  ) {
    boolean accepted = false;
    RetryTask retryToCancel = null;
    BinanceConnection staleConnection = null;
    synchronized (lifecycleMonitor) {
      if (isConnectionDesiredLocked(attempt.generation) && !attempt.cancelled.get()) {
        BinanceConnection existing = binanceConnections.get(symbol);
        if (existing != null && existing.webSocket == webSocket) {
          accepted = true;
        } else if (!isUsable(existing)) {
          if (existing != null) {
            binanceConnections.remove(symbol, existing);
            existing.attempt.intentionalClose.set(true);
            staleConnection = existing;
          }
          binanceConnections.put(symbol, new BinanceConnection(webSocket, attempt));
          retryCounts.remove(symbol);
          retryToCancel = reconnectTasks.remove(symbol);
          accepted = true;
        }
      }

      if (!accepted) {
        attempt.intentionalClose.set(true);
      }
    }

    cancelRetryTask(retryToCancel);
    if (staleConnection != null) {
      closeAttemptSocket(
          staleConnection.attempt,
          staleConnection.webSocket,
          "Replacing stale connection"
      );
    }
    if (!accepted) {
      closeAttemptSocket(attempt, webSocket, "Duplicate or inactive connection");
      return;
    }

    log.info("Binance WebSocket connected: {}", symbol);
    requestNextMessage(symbol, attempt, webSocket);
  }

  private void handleBinanceDisconnect(
      String symbol,
      ConnectionAttempt attempt,
      WebSocket webSocket,
      Throwable error
  ) {
    boolean shouldReconnect;
    synchronized (lifecycleMonitor) {
      BinanceConnection current = binanceConnections.get(symbol);
      if (current != null && current.webSocket == webSocket) {
        binanceConnections.remove(symbol, current);
      }
      shouldReconnect =
          !attempt.cancelled.get()
              && !attempt.intentionalClose.get()
              && isConnectionDesiredLocked(attempt.generation)
              && !isUsable(binanceConnections.get(symbol));
    }

    if (error != null) {
      log.warn("Binance WebSocket disconnected for {}: {}", symbol, errorMessage(error));
    }
    if (shouldReconnect) {
      scheduleReconnect(symbol, attempt.generation);
    }
  }

  private void scheduleReconnect(String symbol, long generation) {
    synchronized (lifecycleMonitor) {
      if (!isConnectionDesiredLocked(generation) || reconnectTasks.containsKey(symbol)) {
        return;
      }

      int retryCount = retryCounts.merge(symbol, 1, Integer::sum);
      long delayMs = retryDelayMillis(retryCount);
      var retryTask = new RetryTask(generation);
      reconnectTasks.put(symbol, retryTask);
      try {
        ScheduledFuture<?> future = reconnectExecutor.schedule(
            () -> runReconnect(symbol, retryTask),
            delayMs,
            TimeUnit.MILLISECONDS
        );
        retryTask.attach(future);
        log.info("Binance WebSocket reconnect scheduled for {} in {} ms", symbol, delayMs);
      } catch (RuntimeException exception) {
        reconnectTasks.remove(symbol, retryTask);
        log.warn("Binance WebSocket reconnect could not be scheduled for {}: {}",
            symbol, exception.getMessage());
      }
    }
  }

  private void runReconnect(String symbol, RetryTask retryTask) {
    synchronized (lifecycleMonitor) {
      if (!reconnectTasks.remove(symbol, retryTask)
          || !isConnectionDesiredLocked(retryTask.generation)) {
        return;
      }
    }
    ensureBinanceConnection(symbol, retryTask.generation);
  }

  private long retryDelayMillis(int retryCount) {
    long delay = initialRetryDelayMs;
    for (int attempt = 1; attempt < retryCount && delay < maxRetryDelayMs; attempt++) {
      delay = delay > maxRetryDelayMs / 2 ? maxRetryDelayMs : delay * 2;
    }
    return Math.min(delay, maxRetryDelayMs);
  }

  private void removeReactSession(
      String sessionId,
      ConcurrentWebSocketSessionDecorator expectedSession
  ) {
    ConnectionCleanup cleanup = null;
    synchronized (lifecycleMonitor) {
      boolean removed = expectedSession == null
          ? activeSessions.remove(sessionId) != null
          : activeSessions.remove(sessionId, expectedSession);
      if (removed && activeSessions.isEmpty()) {
        cleanup = detachAllBinanceConnectionsLocked();
      }
    }
    runConnectionCleanup(cleanup);
  }

  private ConnectionCleanup detachAllBinanceConnectionsLocked() {
    lifecycleGeneration++;
    retryCounts.clear();

    var retries = new ArrayList<>(reconnectTasks.values());
    reconnectTasks.clear();

    var attempts = new ArrayList<>(connectionAttempts.values());
    connectionAttempts.clear();

    var connections = new ArrayList<>(binanceConnections.values());
    binanceConnections.clear();
    connections.forEach(connection -> connection.attempt.intentionalClose.set(true));

    return new ConnectionCleanup(retries, attempts, connections);
  }

  private void runConnectionCleanup(ConnectionCleanup cleanup) {
    if (cleanup == null) {
      return;
    }
    cleanup.retries.forEach(this::cancelRetryTask);
    cleanup.attempts.forEach(ConnectionAttempt::cancel);
    cleanup.connections.forEach(connection ->
        closeAttemptSocket(
            connection.attempt,
            connection.webSocket,
            "No active React sessions"
        )
    );
  }

  private void broadcastPrice(String symbol, String binanceMessage) {
    String price = extractPrice(binanceMessage);
    if (price == null || price.isBlank()) {
      return;
    }

    try {
      BigDecimal parsedPrice = new BigDecimal(price);
      if (parsedPrice.signum() <= 0) {
        return;
      }
      marketPriceService.updateSinglePrice(symbol, parsedPrice, Instant.now());
      String rawPayload = objectMapper.writeValueAsString(Map.of("s", symbol, "p", parsedPrice.toPlainString()));
      broadcastRawPayload(symbol, rawPayload);
    } catch (Exception exception) {
      log.warn("Binance price could not be persisted or serialized for {}: {}", symbol, exception.getMessage());
    }
  }

  private void broadcastRawPayload(String symbol, String rawPayload) {

    log.trace("Binance price received for {}: {}", symbol, rawPayload);
    for (Map.Entry<String, ConcurrentWebSocketSessionDecorator> entry
        : activeSessions.entrySet()) {
      String sessionId = entry.getKey();
      ConcurrentWebSocketSessionDecorator session = entry.getValue();

      if (!session.isOpen()) {
        removeReactSession(sessionId, session);
        continue;
      }

      try {
        session.sendMessage(new TextMessage(rawPayload));
      } catch (Exception exception) {
        removeReactSession(sessionId, session);
        closeReactSession(session, CloseStatus.SERVER_ERROR);
        log.warn("Price could not be sent to React session for {}: {}", symbol, exception.getMessage());
      }
    }
  }

  private String extractPrice(String binanceMessage) {
    try {
      return objectMapper.readTree(binanceMessage).path("p").asText(null);
    } catch (Exception exception) {
      log.warn("Binance message could not be parsed: {}", exception.getMessage());
      return null;
    }
  }

  private void requestNextMessage(
      String symbol,
      ConnectionAttempt attempt,
      WebSocket webSocket
  ) {
    try {
      webSocket.request(1);
    } catch (Exception exception) {
      handleBinanceDisconnect(symbol, attempt, webSocket, exception);
      webSocket.abort();
    }
  }

  private boolean isCurrentConnection(String symbol, WebSocket webSocket) {
    BinanceConnection current = binanceConnections.get(symbol);
    return current != null && current.webSocket == webSocket && isUsable(current);
  }

  private boolean isConnectionDesiredLocked(long generation) {
    return !shuttingDown && !activeSessions.isEmpty() && lifecycleGeneration == generation;
  }

  private boolean isUsable(BinanceConnection connection) {
    if (connection == null) {
      return false;
    }
    try {
      return !connection.webSocket.isInputClosed() && !connection.webSocket.isOutputClosed();
    } catch (Exception ignored) {
      return false;
    }
  }

  private void closeBinanceSocket(WebSocket webSocket, String reason) {
    try {
      if (!webSocket.isOutputClosed()) {
        CompletableFuture<WebSocket> closeFuture = webSocket.sendClose(1000, reason);
        if (closeFuture != null) {
          closeFuture.exceptionally(error -> {
            webSocket.abort();
            return null;
          });
        }
      }
    } catch (Exception exception) {
      webSocket.abort();
    }
  }

  private void closeAttemptSocket(
      ConnectionAttempt attempt,
      WebSocket webSocket,
      String reason
  ) {
    attempt.intentionalClose.set(true);
    if (attempt.closeRequested.compareAndSet(false, true)) {
      closeBinanceSocket(webSocket, reason);
    }
  }

  private void closeReactSession(
      ConcurrentWebSocketSessionDecorator session,
      CloseStatus status
  ) {
    if (session == null) {
      return;
    }
    try {
      if (session.isOpen()) {
        session.close(status);
      }
    } catch (Exception exception) {
      log.debug("React WebSocket session could not be closed: {}", exception.getMessage());
    }
  }

  private void cancelRetryTask(RetryTask retryTask) {
    if (retryTask != null) {
      retryTask.cancel();
    }
  }

  private static String errorMessage(Throwable error) {
    if (error == null) {
      return "unknown error";
    }
    Throwable cause = error.getCause() == null ? error : error.getCause();
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  private static List<List<String>> getSymbolGroups(SupportedSymbolsService supportedSymbols) {
    List<String> allSymbols = supportedSymbols.getSymbols();
    List<List<String>> groups = new ArrayList<>();
    int groupSize = 150;
    for (int i = 0; i < allSymbols.size(); i += groupSize) {
      groups.add(allSymbols.subList(i, Math.min(i + groupSize, allSymbols.size())));
    }
    return groups;
  }

  private static URI getBinanceGroupUri(SupportedSymbolsService supportedSymbols, String groupKey) {
    int index = Integer.parseInt(groupKey.substring(6));
    List<List<String>> groups = getSymbolGroups(supportedSymbols);
    List<String> groupSymbols = groups.get(index);
    
    StringBuilder sb = new StringBuilder("wss://stream.binance.com/stream?streams=");
    for (int i = 0; i < groupSymbols.size(); i++) {
      if (i > 0) sb.append("/");
      sb.append(groupSymbols.get(i).toLowerCase()).append("usdt@trade");
    }
    return URI.create(sb.toString());
  }

  private static BinanceConnector defaultConnector(SupportedSymbolsService supportedSymbols, long connectTimeoutMs) {
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
        .build();
    return (symbol, listener) -> httpClient.newWebSocketBuilder()
        .buildAsync(getBinanceGroupUri(supportedSymbols, symbol), listener);
  }

  private static ScheduledExecutorService newReconnectExecutor() {
    return Executors.newSingleThreadScheduledExecutor(task -> {
      Thread thread = new Thread(task, "binance-websocket-reconnect");
      thread.setDaemon(true);
      return thread;
    });
  }

  @PreDestroy
  void shutdown() {
    ConnectionCleanup cleanup;
    List<ConcurrentWebSocketSessionDecorator> sessions;
    synchronized (lifecycleMonitor) {
      if (shuttingDown) {
        return;
      }
      shuttingDown = true;
      sessions = new ArrayList<>(activeSessions.values());
      activeSessions.clear();
      cleanup = detachAllBinanceConnectionsLocked();
    }

    sessions.forEach(session -> closeReactSession(session, CloseStatus.GOING_AWAY));
    runConnectionCleanup(cleanup);
    if (ownsReconnectExecutor) {
      reconnectExecutor.shutdownNow();
    }
  }

  @FunctionalInterface
  interface BinanceConnector {
    CompletableFuture<WebSocket> connect(String symbol, WebSocket.Listener listener);
  }

  private final class BinanceListener implements WebSocket.Listener {
    private final String symbol;
    private final ConnectionAttempt attempt;
    private final StringBuilder buffer = new StringBuilder();

    private BinanceListener(String symbol, ConnectionAttempt attempt) {
      this.symbol = symbol;
      this.attempt = attempt;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      if (attempt.opened.compareAndSet(false, true)) {
        handleBinanceOpen(symbol, attempt, webSocket);
      }
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      try {
        buffer.append(data);
        if (last) {
          String message = buffer.toString();
          buffer.setLength(0);
          if (isCurrentConnection(symbol, webSocket)) {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(message);
            com.fasterxml.jackson.databind.JsonNode dataNode = root.get("data");
            if (dataNode != null) {
              String pair = dataNode.get("s").asText();
              String priceStr = dataNode.get("p").asText();
              if (pair.endsWith("USDT")) {
                String coin = pair.substring(0, pair.length() - 4);
                BigDecimal parsedPrice = new BigDecimal(priceStr);
                if (parsedPrice.signum() > 0) {
                  marketPriceService.updateSinglePrice(coin, parsedPrice, Instant.now());
                  String rawPayload = objectMapper.writeValueAsString(Map.of("s", coin, "p", parsedPrice.toPlainString()));
                  broadcastRawPayload(coin, rawPayload);
                }
              }
            }
          }
        }
      } catch (Exception exception) {
        log.warn("Binance message handling failed for group {}: {}", symbol, exception.getMessage());
      } finally {
        if (isCurrentConnection(symbol, webSocket)) {
          requestNextMessage(symbol, attempt, webSocket);
        }
      }
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      handleBinanceDisconnect(symbol, attempt, webSocket, error);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      log.info("Binance WebSocket closed for {}: {} - {}", symbol, statusCode, reason);
      handleBinanceDisconnect(symbol, attempt, webSocket, null);
      return CompletableFuture.completedFuture(null);
    }
  }

  private static final class ConnectionAttempt {
    private final long generation;
    private final AtomicBoolean opened = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean intentionalClose = new AtomicBoolean();
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private volatile CompletableFuture<WebSocket> future;

    private ConnectionAttempt(long generation) {
      this.generation = generation;
    }

    private void attach(CompletableFuture<WebSocket> future) {
      this.future = future;
      if (cancelled.get()) {
        future.cancel(true);
      }
    }

    private void cancel() {
      cancelled.set(true);
      CompletableFuture<WebSocket> currentFuture = future;
      if (currentFuture != null) {
        currentFuture.cancel(true);
      }
    }
  }

  private static final class RetryTask {
    private final long generation;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile ScheduledFuture<?> future;

    private RetryTask(long generation) {
      this.generation = generation;
    }

    private void attach(ScheduledFuture<?> future) {
      this.future = future;
      if (cancelled.get()) {
        future.cancel(false);
      }
    }

    private void cancel() {
      cancelled.set(true);
      ScheduledFuture<?> currentFuture = future;
      if (currentFuture != null) {
        currentFuture.cancel(false);
      }
    }
  }

  private record BinanceConnection(WebSocket webSocket, ConnectionAttempt attempt) {}

  private record ConnectionCleanup(
      List<RetryTask> retries,
      List<ConnectionAttempt> attempts,
      List<BinanceConnection> connections
  ) {}
}
