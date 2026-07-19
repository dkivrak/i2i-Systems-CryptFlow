package com.i2i.cryptflow.user;

import com.i2i.cryptflow.portfolio.PortfolioAssetRepository;
import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.trade.TradeTransactionRepository;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final UserRepository users;
  private final WalletRepository wallets;
  private final PasswordEncoder passwordEncoder;
  private final PortfolioAssetRepository portfolioAssets;
  private final TradeTransactionRepository tradeTransactions;

  public AccountService(
      UserRepository users,
      WalletRepository wallets,
      PasswordEncoder passwordEncoder,
      PortfolioAssetRepository portfolioAssets,
      TradeTransactionRepository tradeTransactions
  ) {
    this.users = users;
    this.wallets = wallets;
    this.passwordEncoder = passwordEncoder;
    this.portfolioAssets = portfolioAssets;
    this.tradeTransactions = tradeTransactions;
  }

  @Transactional(readOnly = true)
  public AccountSummary get(UUID userId) {
    var user = findUser(userId);
    var wallet = wallets.findByUserId(userId).orElseThrow();
    return new AccountSummary(user.getId(), user.getEmail(), wallet.getUsdBalance(), user.getCreatedAt());
  }

  @Transactional
  public void changePassword(UUID userId, String oldPassword, String newPassword) {
    var user = findUser(userId);

    if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CREDENTIALS", "Current password is incorrect.");
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    users.save(user);
  }

  @Transactional
  public void delete(UUID userId) {
    var user = findUser(userId);
    var wallet = wallets.findByUserId(userId).orElseThrow();

    tradeTransactions.deleteByUserId(userId);
    portfolioAssets.deleteByWalletId(wallet.getId());
    wallets.delete(wallet);
    users.delete(user);
  }

  private User findUser(UUID userId) {
    return users.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
  }

  public record AccountSummary(UUID id, String email, BigDecimal usdBalance, Instant createdAt) {}
}
