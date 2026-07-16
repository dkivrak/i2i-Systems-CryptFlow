package com.i2i.cryptflow.portfolio;

import com.i2i.cryptflow.shared.model.AssetSymbol;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PortfolioAssetRepository extends JpaRepository<PortfolioAsset, UUID> {
  List<PortfolioAsset> findByWalletIdOrderBySymbol(UUID walletId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from PortfolioAsset a where a.wallet.id=:walletId and a.symbol=:symbol")
  Optional<PortfolioAsset> findForUpdate(@Param("walletId") UUID walletId, @Param("symbol") AssetSymbol symbol);
  void deleteByWalletId(UUID walletId);
}

