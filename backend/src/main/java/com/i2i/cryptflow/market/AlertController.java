package com.i2i.cryptflow.market;

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
@RequestMapping("/api/alerts")
public class AlertController {
  private final AlertService alertService;

  public AlertController(AlertService alertService) {
    this.alertService = alertService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  AlertDto create(@AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateAlertRequest req) {
    var alert = alertService.create(userId, req.symbol(), req.targetPrice(), req.condition());
    return from(alert);
  }

  @GetMapping
  List<AlertDto> getActive(@AuthenticationPrincipal UUID userId) {
    return alertService.getActive(userId).stream().map(this::from).toList();
  }

  @GetMapping("/triggered")
  List<AlertDto> getTriggered(@AuthenticationPrincipal UUID userId) {
    return alertService.getTriggered(userId).stream().map(this::from).toList();
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    alertService.delete(userId, id);
  }

  private AlertDto from(PriceAlert alert) {
    return new AlertDto(
        alert.getId(),
        alert.getSymbol(),
        alert.getTargetPrice(),
        alert.getCondition(),
        alert.isTriggered(),
        alert.getCreatedAt()
    );
  }

  public record CreateAlertRequest(
      @NotBlank String symbol,
      @NotNull @Positive BigDecimal targetPrice,
      @NotBlank String condition // ABOVE, BELOW
  ) {}

  public record AlertDto(
      UUID id,
      String symbol,
      BigDecimal targetPrice,
      String condition,
      boolean isTriggered,
      Instant createdAt
  ) {}
}
