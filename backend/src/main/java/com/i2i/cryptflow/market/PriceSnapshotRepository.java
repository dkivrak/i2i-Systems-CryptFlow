package com.i2i.cryptflow.market;

import com.i2i.cryptflow.shared.model.AssetSymbol;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {
  Optional<PriceSnapshot> findFirstBySymbolOrderByRecordedAtDesc(AssetSymbol symbol);
  List<PriceSnapshot> findTop20BySymbolOrderByRecordedAtDesc(AssetSymbol symbol);
}
