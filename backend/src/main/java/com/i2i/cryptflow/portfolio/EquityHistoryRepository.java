package com.i2i.cryptflow.portfolio;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquityHistoryRepository extends JpaRepository<EquityHistory, UUID> {
  List<EquityHistory> findByUserIdOrderByRecordedAtAsc(UUID userId);
}
