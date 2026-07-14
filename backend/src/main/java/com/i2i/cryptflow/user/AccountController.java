package com.i2i.cryptflow.user;

import com.i2i.cryptflow.shared.error.ApiException;
import com.i2i.cryptflow.wallet.WalletRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final UserRepository users;
    private final WalletRepository wallets;
    private final PasswordEncoder passwordEncoder;

    public AccountController(UserRepository users, WalletRepository wallets, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.wallets = wallets;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal UUID userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
        var wallet = wallets.findByUserId(userId).orElseThrow();
        return new MeResponse(user.getId(), user.getEmail(), wallet.getUsdBalance(), user.getCreatedAt());
    }

    @PostMapping("/me/change-password")
    @Transactional
    public void changePassword(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        
        var user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CREDENTIALS", "Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
    }

    public record MeResponse(UUID id, String email, BigDecimal usdBalance, java.time.Instant createdAt) {}
    
    public record ChangePasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 8, max = 72, message = "New password must be at least 8 characters long.") String newPassword
    ) {}
}

