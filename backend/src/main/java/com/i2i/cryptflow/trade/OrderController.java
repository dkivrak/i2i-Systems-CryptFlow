package com.i2i.cryptflow.trade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  OrderDto place(@AuthenticationPrincipal UUID userId, @Valid @RequestBody PlaceOrderRequest req) {
    var order = orderService.place(userId, req.symbol(), req.side(), req.type(), req.targetPrice(), req.quantity());
    return from(order);
  }

  @GetMapping
  List<OrderDto> getActive(@AuthenticationPrincipal UUID userId) {
    return orderService.getActiveOrders(userId).stream().map(this::from).toList();
  }

  @GetMapping("/history")
  List<OrderDto> getHistory(@AuthenticationPrincipal UUID userId) {
    return orderService.getHistory(userId).stream().map(this::from).toList();
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void cancel(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    orderService.cancel(userId, id);
  }

  private OrderDto from(LimitOrder order) {
    return new OrderDto(
        order.getId(),
        order.getSymbol(),
        order.getSide(),
        order.getType(),
        order.getTargetPrice(),
        order.getQuantity(),
        order.getStatus(),
        order.getCreatedAt()
    );
  }

  public record PlaceOrderRequest(
      @NotBlank String symbol,
      @NotBlank String side,
      @NotBlank String type,
      @NotNull @Positive BigDecimal targetPrice,
      @NotNull @Positive BigDecimal quantity
  ) {}

  public record OrderDto(
      UUID id,
      String symbol,
      String side,
      String type,
      BigDecimal targetPrice,
      BigDecimal quantity,
      String status,
      Instant createdAt
  ) {}
}
