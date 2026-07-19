package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.ExternalPriceProvider;
import com.i2i.cryptflow.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {
  private final PriceAlertRepository alerts;
  private final UserRepository users;
  private final ExternalPriceProvider supportedSymbols;

  public AlertService(PriceAlertRepository alerts, UserRepository users, ExternalPriceProvider supportedSymbols) {
    this.alerts = alerts;
    this.users = users;
    this.supportedSymbols = supportedSymbols;
  }

  @Transactional
  public PriceAlert create(UUID userId, String symbol, BigDecimal targetPrice, String condition) {
    String normalizedSymbol = normalize(symbol);
    String normalizedCondition = normalize(condition);
    if (!supportedSymbols.isSupported(normalizedSymbol)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SYMBOL", "Symbol '" + normalizedSymbol + "' is not supported.");
    }
    if (targetPrice == null || targetPrice.signum() <= 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_PRICE", "Target price must be positive.");
    }
    if (!normalizedCondition.equals("ABOVE") && !normalizedCondition.equals("BELOW")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONDITION", "Condition must be ABOVE or BELOW.");
    }
    var user = users.findById(userId).orElseThrow();
    return alerts.save(new PriceAlert(user, normalizedSymbol, targetPrice, normalizedCondition));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  @Transactional
  public void delete(UUID userId, UUID alertId) {
    var alert = alerts.findById(alertId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "Alert not found."));
    if (!alert.getUser().getId().equals(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not own this alert.");
    }
    alerts.delete(alert);
  }

  @Transactional
  public void checkAlerts(Map<String, BigDecimal> currentPrices) {
    List<PriceAlert> activeAlerts = alerts.findByIsTriggered(false);
    for (PriceAlert alert : activeAlerts) {
      BigDecimal currentPrice = currentPrices.get(alert.getSymbol());
      if (currentPrice == null) continue;

      boolean trigger = false;
      if (alert.getCondition().equalsIgnoreCase("ABOVE") && currentPrice.compareTo(alert.getTargetPrice()) >= 0) {
        trigger = true;
      } else if (alert.getCondition().equalsIgnoreCase("BELOW") && currentPrice.compareTo(alert.getTargetPrice()) <= 0) {
        trigger = true;
      }

      if (trigger) {
        alert.setTriggered(true);
        alert.setTriggeredAt(java.time.Instant.now());
        alerts.save(alert);
      }
    }
  }

  @Transactional(readOnly = true)
  public List<PriceAlert> getActive(UUID userId) {
    return alerts.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .filter(a -> !a.isTriggered())
        .toList();
  }

  @Transactional(readOnly = true)
  public List<PriceAlert> getTriggered(UUID userId) {
    return alerts.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .filter(PriceAlert::isTriggered)
        .toList();
  }
}
