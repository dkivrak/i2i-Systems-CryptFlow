package com.i2i.cryptflow.trade;

import com.i2i.cryptflow.shared.model.AssetSymbol;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trades")
public class TradeController {
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final TradeService service;

  public TradeController(TradeService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  TradeService.TradeResult execute(
      @AuthenticationPrincipal UUID userId,
      @Valid @RequestBody TradeRequest r) {
    return service.execute(userId, r.symbol(), r.side(), r.quantity());
  }

  @GetMapping
  Page<TradeService.TradeResult> history(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    return service.history(userId, page, size);
  }

  public record TradeRequest(@NotNull AssetSymbol symbol, @NotNull TradeSide side, @NotNull BigDecimal quantity) {}
}
