package com.i2i.cryptflow.user;

import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.wallet.WalletRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class AccountController {
  private final UserRepository users; private final WalletRepository wallets;
  public AccountController(UserRepository users,WalletRepository wallets){this.users=users;this.wallets=wallets;}
  @GetMapping("/me") MeResponse me(@AuthenticationPrincipal UUID userId){
    var user=users.findById(userId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","Kullanıcı bulunamadı."));
    var wallet=wallets.findByUserId(userId).orElseThrow();return new MeResponse(user.getId(),user.getEmail(),wallet.getUsdBalance());
  }
  public record MeResponse(UUID id,String email,BigDecimal usdBalance){}
}

