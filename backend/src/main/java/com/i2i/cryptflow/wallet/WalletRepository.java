package com.i2i.cryptflow.wallet;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
  Optional<Wallet> findByUserId(UUID userId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from Wallet w where w.user.id=:userId")
  Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);
}

