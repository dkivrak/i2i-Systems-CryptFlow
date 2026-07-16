package com.i2i.cryptflow.trade;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeTransactionRepository extends JpaRepository<TradeTransaction, UUID> {
  Page<TradeTransaction> findByUserIdOrderByExecutedAtDesc(UUID userId, Pageable pageable);
  List<TradeTransaction> findTop20ByUserIdOrderByExecutedAtDesc(UUID userId);
  void deleteByUserId(UUID userId);
}

