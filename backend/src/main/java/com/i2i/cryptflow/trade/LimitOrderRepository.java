package com.i2i.cryptflow.trade;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LimitOrderRepository extends JpaRepository<LimitOrder, UUID> {
  List<LimitOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);
  List<LimitOrder> findByStatus(String status);
}
