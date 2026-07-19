package com.i2i.cryptflow.trade;

import com.i2i.cryptflow.shared.config.OpenApiConfig;
import com.i2i.cryptflow.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "Place, inspect, and cancel limit or stop-loss paper orders.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class OrderController {
  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Place a pending order", description = "Accepts LIMIT BUY/SELL and STOP_LOSS SELL orders. Text fields are normalized to uppercase before validation and storage.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Pending order created.",
          content = @Content(
              schema = @Schema(implementation = OrderDto.class),
              examples = @ExampleObject(value = "{\"id\":\"ad442e71-22d2-42c5-821b-58d5aee11ebd\",\"symbol\":\"BTC\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"targetPrice\":58000.00,\"quantity\":0.00100000,\"status\":\"PENDING\",\"createdAt\":\"2026-07-19T12:00:00Z\"}")
          )
      ),
      @ApiResponse(responseCode = "400", description = "Malformed body, unsupported symbol, invalid side/type combination, or non-positive price/quantity.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "422", description = "Insufficient available virtual USD or asset quantity after pending-order reservations.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  OrderDto place(
      @AuthenticationPrincipal UUID userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "Pending order definition. STOP_LOSS is supported only with side SELL.",
          content = @Content(
              schema = @Schema(implementation = PlaceOrderRequest.class),
              examples = @ExampleObject(value = "{\"symbol\":\"BTC\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"targetPrice\":58000.00,\"quantity\":0.00100000}")
          )
      )
      @Valid @RequestBody PlaceOrderRequest req) {
    var order = orderService.place(userId, req.symbol(), req.side(), req.type(), req.targetPrice(), req.quantity());
    return from(order);
  }

  @GetMapping
  @Operation(summary = "List pending orders")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Pending orders, newest first.", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderDto.class)))),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  List<OrderDto> getActive(@AuthenticationPrincipal UUID userId) {
    return orderService.getActiveOrders(userId).stream().map(this::from).toList();
  }

  @GetMapping("/history")
  @Operation(summary = "List completed order history", description = "Returns executed and cancelled orders, newest first.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Executed and cancelled orders.", content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrderDto.class)))),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  List<OrderDto> getHistory(@AuthenticationPrincipal UUID userId) {
    return orderService.getHistory(userId).stream().map(this::from).toList();
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Cancel a pending order")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Order cancelled."),
      @ApiResponse(responseCode = "400", description = "The order is no longer pending.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "403", description = "The order belongs to another user.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "404", description = "Order not found.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  void cancel(
      @AuthenticationPrincipal UUID userId,
      @Parameter(description = "Order identifier.", example = "ad442e71-22d2-42c5-821b-58d5aee11ebd", required = true)
      @PathVariable UUID id) {
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

  @Schema(name = "PlaceOrderRequest", description = "Limit or stop-loss order request.")
  public record PlaceOrderRequest(
      @Schema(description = "Supported asset symbol; normalized to uppercase.", example = "BTC")
      @NotBlank String symbol,
      @Schema(description = "Order direction.", example = "BUY", allowableValues = {"BUY", "SELL"})
      @NotBlank String side,
      @Schema(description = "Order trigger type. STOP_LOSS requires side SELL.", example = "LIMIT", allowableValues = {"LIMIT", "STOP_LOSS"})
      @NotBlank String type,
      @Schema(description = "Strictly positive target unit price in USD.", example = "58000.00", minimum = "0", exclusiveMinimum = true)
      @NotNull @Positive BigDecimal targetPrice,
      @Schema(description = "Strictly positive asset quantity.", example = "0.00100000", minimum = "0", exclusiveMinimum = true)
      @NotNull @Positive BigDecimal quantity
  ) {}

  @Schema(name = "Order", description = "Persisted pending, executed, or cancelled order.")
  public record OrderDto(
      @Schema(description = "Order identifier.", example = "ad442e71-22d2-42c5-821b-58d5aee11ebd", format = "uuid") UUID id,
      @Schema(description = "Uppercase asset symbol.", example = "BTC") String symbol,
      @Schema(description = "Order direction.", example = "BUY", allowableValues = {"BUY", "SELL"}) String side,
      @Schema(description = "Order trigger type.", example = "LIMIT", allowableValues = {"LIMIT", "STOP_LOSS"}) String type,
      @Schema(description = "Target unit price in USD.", example = "58000.00") BigDecimal targetPrice,
      @Schema(description = "Requested asset quantity.", example = "0.00100000") BigDecimal quantity,
      @Schema(description = "Current order state.", example = "PENDING", allowableValues = {"PENDING", "EXECUTED", "CANCELLED"}) String status,
      @Schema(description = "Order creation time.", example = "2026-07-19T12:00:00Z", format = "date-time") Instant createdAt
  ) {}
}
