package com.i2i.cryptflow.market;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {
  Optional<PriceSnapshot> findFirstBySymbolOrderByRecordedAtDesc(String symbol);
  List<PriceSnapshot> findTop20BySymbolOrderByRecordedAtDesc(String symbol);
}
