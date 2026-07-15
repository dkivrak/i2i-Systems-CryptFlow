package com.i2i.cryptflow.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.i2i.cryptflow.market.MarketPriceService;
import com.i2i.cryptflow.shared.model.AssetSymbol;
import java.io.IOException;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class PriceWebSocketHandlerTest {
  private static final long RETRY_DELAY_MS = 1_000;

  private MarketPriceService marketPriceService;
  private ScheduledExecutorService reconnectExecutor;
  private ScheduledFuture<?> scheduledFuture;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    marketPriceService = mock(MarketPriceService.class);
    reconnectExecutor = mock(ScheduledExecutorService.class);
    scheduledFuture = mock(ScheduledFuture.class);
    doReturn(scheduledFuture).when(reconnectExecutor).schedule(
        any(Runnable.class),
        anyLong(),
        eq(TimeUnit.MILLISECONDS)
    );
  }

  @Test
  void doesNotCreateDuplicateConnectionsWhileConnectIsPending() {
    var connector = new PendingConnector();
    var handler = handler(connector);
    WebSocketSession firstSession = reactSession("react-1");
    WebSocketSession secondSession = reactSession("react-2");

    handler.afterConnectionEstablished(firstSession);
    handler.afterConnectionEstablished(secondSession);

    for (AssetSymbol symbol : AssetSymbol.values()) {
      assertEquals(1, connector.calls(symbol).size());
    }

    handler.afterConnectionClosed(firstSession, CloseStatus.NORMAL);
    handler.afterConnectionClosed(secondSession, CloseStatus.NORMAL);
    verifyNoInteractions(reconnectExecutor);
  }

  @Test
  void reconnectsAfterRemoteCloseWhenReactSessionIsActive() {
    assertReconnectAfter(DisconnectSignal.CLOSE);
  }

  @Test
  void reconnectsAfterRemoteErrorWhenReactSessionIsActive() {
    assertReconnectAfter(DisconnectSignal.ERROR);
  }

  @Test
  void doesNotReconnectAfterIntentionalCloseWithoutReactSessions() {
    var connector = new OpeningConnector();
    var handler = handler(connector);
    WebSocketSession session = reactSession("react-1");
    handler.afterConnectionEstablished(session);
    ConnectionCall btc = connector.calls(AssetSymbol.BTC).getFirst();

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    btc.listener().onClose(btc.webSocket(), 1000, "normal");

    verify(reconnectExecutor, never()).schedule(
        any(Runnable.class),
        anyLong(),
        eq(TimeUnit.MILLISECONDS)
    );
    for (AssetSymbol symbol : AssetSymbol.values()) {
      verify(connector.calls(symbol).getFirst().webSocket())
          .sendClose(1000, "No active React sessions");
    }
  }

  @Test
  void closesSocketThatCompletesAfterItsAttemptWasCancelled() {
    var connector = new NonCancellablePendingConnector();
    var handler = handler(connector);
    WebSocketSession session = reactSession("react-1");
    handler.afterConnectionEstablished(session);
    ConnectionCall btc = connector.calls(AssetSymbol.BTC).getFirst();

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    btc.future().complete(btc.webSocket());

    verify(btc.webSocket()).sendClose(1000, "Inactive connection attempt");
    verifyNoInteractions(reconnectExecutor);
  }

  @Test
  void successfulReconnectResetsExponentialBackoff() {
    var connector = new OpeningConnector();
    var handler = handler(connector);
    WebSocketSession session = reactSession("react-1");
    handler.afterConnectionEstablished(session);

    ConnectionCall firstBtc = connector.calls(AssetSymbol.BTC).getFirst();
    firstBtc.listener().onError(firstBtc.webSocket(), new IOException("first failure"));
    Runnable firstRetry = scheduledRetry();
    firstRetry.run();
    assertEquals(2, connector.calls(AssetSymbol.BTC).size());

    clearInvocations(reconnectExecutor);
    ConnectionCall secondBtc = connector.calls(AssetSymbol.BTC).get(1);
    secondBtc.listener().onError(secondBtc.webSocket(), new IOException("second failure"));

    verify(reconnectExecutor).schedule(
        any(Runnable.class),
        eq(RETRY_DELAY_MS),
        eq(TimeUnit.MILLISECONDS)
    );
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);
  }

  private void assertReconnectAfter(DisconnectSignal signal) {
    var connector = new OpeningConnector();
    var handler = handler(connector);
    WebSocketSession session = reactSession("react-1");
    handler.afterConnectionEstablished(session);
    ConnectionCall firstBtc = connector.calls(AssetSymbol.BTC).getFirst();

    if (signal == DisconnectSignal.CLOSE) {
      firstBtc.listener().onClose(firstBtc.webSocket(), 1006, "lost");
    } else {
      firstBtc.listener().onError(firstBtc.webSocket(), new IOException("lost"));
    }

    scheduledRetry().run();

    assertEquals(2, connector.calls(AssetSymbol.BTC).size());
    assertEquals(1, connector.calls(AssetSymbol.ETH).size());
    assertEquals(1, connector.calls(AssetSymbol.SOL).size());
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);
  }

  private Runnable scheduledRetry() {
    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(reconnectExecutor).schedule(
        task.capture(),
        eq(RETRY_DELAY_MS),
        eq(TimeUnit.MILLISECONDS)
    );
    return task.getValue();
  }

  private PriceWebSocketHandler handler(PriceWebSocketHandler.BinanceConnector connector) {
    return new PriceWebSocketHandler(
        new ObjectMapper(),
        marketPriceService,
        connector,
        reconnectExecutor,
        10_000,
        RETRY_DELAY_MS,
        30_000
    );
  }

  private WebSocketSession reactSession(String id) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  private enum DisconnectSignal {
    CLOSE,
    ERROR
  }

  private record ConnectionCall(
      WebSocket.Listener listener,
      WebSocket webSocket,
      CompletableFuture<WebSocket> future
  ) {}

  private abstract static class RecordingConnector
      implements PriceWebSocketHandler.BinanceConnector {
    private final EnumMap<AssetSymbol, List<ConnectionCall>> calls =
        new EnumMap<>(AssetSymbol.class);

    protected ConnectionCall record(
        AssetSymbol symbol,
        WebSocket.Listener listener,
        CompletableFuture<WebSocket> future
    ) {
      WebSocket webSocket = mock(WebSocket.class);
      when(webSocket.isInputClosed()).thenReturn(false);
      when(webSocket.isOutputClosed()).thenReturn(false);
      var call = new ConnectionCall(listener, webSocket, future);
      calls.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(call);
      return call;
    }

    List<ConnectionCall> calls(AssetSymbol symbol) {
      return calls.getOrDefault(symbol, List.of());
    }
  }

  private static final class PendingConnector extends RecordingConnector {
    @Override
    public CompletableFuture<WebSocket> connect(
        AssetSymbol symbol,
        WebSocket.Listener listener
    ) {
      var future = new CompletableFuture<WebSocket>();
      record(symbol, listener, future);
      return future;
    }
  }

  private static final class OpeningConnector extends RecordingConnector {
    @Override
    public CompletableFuture<WebSocket> connect(
        AssetSymbol symbol,
        WebSocket.Listener listener
    ) {
      var future = new CompletableFuture<WebSocket>();
      ConnectionCall call = record(symbol, listener, future);
      listener.onOpen(call.webSocket());
      future.complete(call.webSocket());
      return future;
    }
  }

  private static final class NonCancellablePendingConnector extends RecordingConnector {
    @Override
    public CompletableFuture<WebSocket> connect(
        AssetSymbol symbol,
        WebSocket.Listener listener
    ) {
      var future = new CompletableFuture<WebSocket>() {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
          return false;
        }
      };
      record(symbol, listener, future);
      return future;
    }
  }
}
