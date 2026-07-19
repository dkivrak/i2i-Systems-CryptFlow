package com.i2i.cryptflow.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.i2i.cryptflow.portfolio.PortfolioAssetRepository;
import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.trade.TradeTransactionRepository;
import com.i2i.cryptflow.wallet.Wallet;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountServiceTest {
  private final UserRepository users = mock(UserRepository.class);
  private final WalletRepository wallets = mock(WalletRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final PortfolioAssetRepository portfolioAssets = mock(PortfolioAssetRepository.class);
  private final TradeTransactionRepository tradeTransactions = mock(TradeTransactionRepository.class);
  private final AccountService service = new AccountService(
      users, wallets, passwordEncoder, portfolioAssets, tradeTransactions);

  @Test
  void returnsAccountAndWalletSummary() {
    var user = new User("trader@example.com", "hash");
    var wallet = new Wallet(user, new BigDecimal("14950.00"));
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(wallets.findByUserId(user.getId())).thenReturn(Optional.of(wallet));

    var result = service.get(user.getId());

    assertEquals(user.getId(), result.id());
    assertEquals(user.getEmail(), result.email());
    assertEquals(wallet.getUsdBalance(), result.usdBalance());
    assertEquals(user.getCreatedAt(), result.createdAt());
  }

  @Test
  void rejectsIncorrectCurrentPasswordWithoutSaving() {
    var user = new User("trader@example.com", "old-hash");
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

    var exception = assertThrows(
        ApiException.class,
        () -> service.changePassword(user.getId(), "wrong", "new-password"));

    assertEquals("INVALID_CREDENTIALS", exception.getCode());
    verify(users, never()).save(user);
  }

  @Test
  void changesPasswordThroughEncoderAndRepository() {
    var user = new User("trader@example.com", "old-hash");
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
    when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

    service.changePassword(user.getId(), "old-password", "new-password");

    assertEquals("new-hash", user.getPasswordHash());
    verify(users).save(user);
  }

  @Test
  void deletesAccountDataInDependencyOrder() {
    var user = new User("trader@example.com", "hash");
    var wallet = new Wallet(user, new BigDecimal("15000.00"));
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(wallets.findByUserId(user.getId())).thenReturn(Optional.of(wallet));

    service.delete(user.getId());

    var ordered = inOrder(users, wallets, tradeTransactions, portfolioAssets);
    ordered.verify(users).findById(user.getId());
    ordered.verify(wallets).findByUserId(user.getId());
    ordered.verify(tradeTransactions).deleteByUserId(user.getId());
    ordered.verify(portfolioAssets).deleteByWalletId(wallet.getId());
    ordered.verify(wallets).delete(wallet);
    ordered.verify(users).delete(user);
  }

  @Test
  void reportsMissingUserWithStructuredError() {
    var userId = UUID.randomUUID();
    when(users.findById(userId)).thenReturn(Optional.empty());

    var exception = assertThrows(ApiException.class, () -> service.get(userId));

    assertEquals("USER_NOT_FOUND", exception.getCode());
  }
}
