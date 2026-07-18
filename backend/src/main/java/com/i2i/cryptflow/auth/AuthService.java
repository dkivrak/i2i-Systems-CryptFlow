package com.i2i.cryptflow.auth;

import com.i2i.cryptflow.portfolio.*;
import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.ExternalPriceProvider;
import com.i2i.cryptflow.user.*;
import com.i2i.cryptflow.wallet.*;
import java.math.*;
import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final long MIN_INITIAL_BALANCE_CENTS = 1_000_000L;
  private static final int BALANCE_RANGE_CENTS = 1_000_001;

  private final UserRepository users;
  private final WalletRepository wallets;
  private final PortfolioAssetRepository assets;
  private final PasswordEncoder passwords;
  private final SessionService sessions;
  private final ExternalPriceProvider supportedSymbols;
  private final SecureRandom random = new SecureRandom();

  public AuthService(UserRepository users,
                     WalletRepository wallets,
                     PortfolioAssetRepository assets,
                     PasswordEncoder passwordEncoder,
                     SessionService sessions,
                     ExternalPriceProvider supportedSymbols) {
    this.users = users;
    this.wallets = wallets;
    this.assets = assets;
    this.passwords = passwordEncoder;
    this.sessions = sessions;
    this.supportedSymbols = supportedSymbols;
  }

  @Transactional
  public RegisterResult register(String rawEmail, String password) {
    String email = normalizeEmail(rawEmail);
    if (users.existsByEmail(email))
      throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "This email address is already registered.");
    var user = users.save(new User(email, passwords.encode(password)));
    var balance = generateInitialBalance();
    var wallet = wallets.save(new Wallet(user, balance));
    return new RegisterResult(user.getId(), user.getEmail(), balance);
  }

  public LoginResult login(String rawEmail, String password) {
    var user = users.findByEmail(normalizeEmail(rawEmail)).orElseThrow(this::badCredentials);
    if (!passwords.matches(password, user.getPasswordHash()))
      throw badCredentials();
    var session = sessions.create(user.getId());
    return new LoginResult(session.token(), session.expiresAt(), user.getId(), user.getEmail());
  }

  private String normalizeEmail(String rawEmail) {
    return rawEmail.trim().toLowerCase(Locale.ROOT);
  }

  private BigDecimal generateInitialBalance() {
    return BigDecimal.valueOf(MIN_INITIAL_BALANCE_CENTS + random.nextInt(BALANCE_RANGE_CENTS), 2);
  }

  private ApiException badCredentials() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
  }

  public record RegisterResult(java.util.UUID userId, String email, BigDecimal initialBalance) {}

  public record LoginResult(java.util.UUID token, java.time.Instant expiresAt, java.util.UUID userId, String email) {}
}
