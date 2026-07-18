package com.i2i.cryptflow.trade;

import com.i2i.cryptflow.portfolio.PortfolioAssetRepository;
import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.user.UserRepository;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final LimitOrderRepository orders;
  private final WalletRepository wallets;
  private final PortfolioAssetRepository assets;
  private final UserRepository users;
  private final TradeService tradeService;

  public OrderService(
      LimitOrderRepository orders,
      WalletRepository wallets,
      PortfolioAssetRepository assets,
      UserRepository users,
      TradeService tradeService
  ) {
    this.orders = orders;
    this.wallets = wallets;
    this.assets = assets;
    this.users = users;
    this.tradeService = tradeService;
  }

  @Transactional
  public LimitOrder place(UUID userId, String symbol, String side, String type, BigDecimal targetPrice, BigDecimal quantity) {
    var user = users.findById(userId).orElseThrow();
    var wallet = wallets.findByUserId(userId).orElseThrow();
    var total = targetPrice.multiply(quantity);

    if (quantity.signum() <= 0 || targetPrice.signum() <= 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Quantity and target price must be positive.");
    }

    List<LimitOrder> pending = orders.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .filter(o -> o.getStatus().equals("PENDING"))
        .toList();

    if (side.equalsIgnoreCase("BUY")) {
      BigDecimal lockedUsd = pending.stream()
          .filter(o -> o.getSide().equalsIgnoreCase("BUY"))
          .map(o -> o.getTargetPrice().multiply(o.getQuantity()))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal availableUsd = wallet.getUsdBalance().subtract(lockedUsd);
      if (availableUsd.compareTo(total) < 0) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Insufficient available USD balance (some funds may be locked in other limit orders).");
      }
    } else {
      BigDecimal lockedCoin = pending.stream()
          .filter(o -> o.getSide().equalsIgnoreCase("SELL") && o.getSymbol().equalsIgnoreCase(symbol))
          .map(LimitOrder::getQuantity)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      var asset = assets.findByWalletIdAndSymbol(wallet.getId(), symbol)
          .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_ASSET", "You do not own this asset."));
      BigDecimal availableCoin = asset.getQuantity().subtract(lockedCoin);
      if (availableCoin.compareTo(quantity) < 0) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_ASSET", "Insufficient available coin quantity (some quantity may be locked in other sell limit orders).");
      }
    }

    return orders.save(new LimitOrder(user, symbol.toUpperCase(), side.toUpperCase(), type.toUpperCase(), targetPrice, quantity));
  }

  @Transactional
  public void cancel(UUID userId, UUID orderId) {
    var order = orders.findById(orderId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found."));
    if (!order.getUser().getId().equals(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not own this order.");
    }
    if (!order.getStatus().equals("PENDING")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER_NOT_PENDING", "Only pending orders can be cancelled.");
    }
    order.setStatus("CANCELLED");
  }

  @Transactional
  public void processPendingOrders(Map<String, BigDecimal> currentPrices) {
    List<LimitOrder> pending = orders.findByStatus("PENDING");
    for (LimitOrder order : pending) {
      BigDecimal currentPrice = currentPrices.get(order.getSymbol());
      if (currentPrice == null) continue;

      boolean trigger = false;
      if (order.getType().equalsIgnoreCase("LIMIT")) {
        if (order.getSide().equalsIgnoreCase("BUY") && currentPrice.compareTo(order.getTargetPrice()) <= 0) {
          trigger = true;
        } else if (order.getSide().equalsIgnoreCase("SELL") && currentPrice.compareTo(order.getTargetPrice()) >= 0) {
          trigger = true;
        }
      } else if (order.getType().equalsIgnoreCase("STOP_LOSS")) {
        if (order.getSide().equalsIgnoreCase("SELL") && currentPrice.compareTo(order.getTargetPrice()) <= 0) {
          trigger = true;
        }
      }

      if (trigger) {
        try {
          tradeService.execute(
              order.getUser().getId(),
              order.getSymbol(),
              TradeSide.valueOf(order.getSide()),
              order.getQuantity()
          );
          order.setStatus("EXECUTED");
        } catch (Exception ex) {
          order.setStatus("CANCELLED");
        }
      }
    }
  }

  @Transactional(readOnly = true)
  public List<LimitOrder> getActiveOrders(UUID userId) {
    return orders.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .filter(o -> o.getStatus().equals("PENDING"))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<LimitOrder> getHistory(UUID userId) {
    return orders.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .filter(o -> !o.getStatus().equals("PENDING"))
        .toList();
  }
}
