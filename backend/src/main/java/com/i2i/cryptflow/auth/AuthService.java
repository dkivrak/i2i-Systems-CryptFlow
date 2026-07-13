package com.i2i.cryptflow.auth;

import com.i2i.cryptflow.portfolio.*;
import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.shared.model.AssetSymbol;
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
  private final UserRepository users; private final WalletRepository wallets; private final PortfolioAssetRepository assets;
  private final PasswordEncoder passwords; private final SessionService sessions; private final SecureRandom random=new SecureRandom();
  public AuthService(UserRepository u,WalletRepository w,PortfolioAssetRepository a,PasswordEncoder p,SessionService s){users=u;wallets=w;assets=a;passwords=p;sessions=s;}
  @Transactional public RegisterResult register(String rawEmail,String password){
    String email=rawEmail.trim().toLowerCase(Locale.ROOT);
    if(users.existsByEmail(email))throw new ApiException(HttpStatus.CONFLICT,"EMAIL_ALREADY_EXISTS","Bu e-posta adresi zaten kayıtlı.");
    var user=users.save(new User(email,passwords.encode(password)));
    var balance=BigDecimal.valueOf(1_000_000L+random.nextInt(1_000_001),2);
    var wallet=wallets.save(new Wallet(user,balance));
    for(var symbol:AssetSymbol.values())assets.save(new PortfolioAsset(wallet,symbol));
    return new RegisterResult(user.getId(),user.getEmail(),balance);
  }
  public LoginResult login(String rawEmail,String password){
    var user=users.findByEmail(rawEmail.trim().toLowerCase(Locale.ROOT)).orElseThrow(this::badCredentials);
    if(!passwords.matches(password,user.getPasswordHash()))throw badCredentials();
    var session=sessions.create(user.getId());
    return new LoginResult(session.token(),session.expiresAt(),user.getId(),user.getEmail());
  }
  private ApiException badCredentials(){return new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","E-posta veya parola hatalı.");}
  public record RegisterResult(java.util.UUID userId,String email,BigDecimal initialBalance){}
  public record LoginResult(java.util.UUID token,java.time.Instant expiresAt,java.util.UUID userId,String email){}
}

