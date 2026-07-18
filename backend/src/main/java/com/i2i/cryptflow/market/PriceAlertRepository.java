package com.i2i.cryptflow.market;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, UUID> {
  List<PriceAlert> findByUserIdOrderByCreatedAtDesc(UUID userId);
  List<PriceAlert> findByIsTriggered(boolean isTriggered);
}
