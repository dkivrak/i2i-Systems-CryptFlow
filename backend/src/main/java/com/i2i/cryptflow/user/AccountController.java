package com.i2i.cryptflow.user;

import com.i2i.cryptflow.shared.config.OpenApiConfig;
import com.i2i.cryptflow.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Account", description = "Read and manage the authenticated account.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current account")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Authenticated account and wallet summary.",
            content = @Content(
                schema = @Schema(implementation = MeResponse.class),
                examples = @ExampleObject(value = "{\"id\":\"018f3e55-7a55-7b2d-9c11-4d91b44f8b10\",\"email\":\"trader@example.com\",\"usdBalance\":14950.00,\"createdAt\":\"2026-07-19T12:00:00Z\"}")
            )
        ),
        @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "The authenticated user no longer exists.", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MeResponse me(@AuthenticationPrincipal UUID userId) {
        var account = accountService.get(userId);
        return new MeResponse(account.id(), account.email(), account.usdBalance(), account.createdAt());
    }

    @PostMapping("/me/change-password")
    @Operation(summary = "Change the current account password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password changed successfully; the response body is empty."),
        @ApiResponse(responseCode = "400", description = "Malformed body, password validation failure, or incorrect current password.", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "The authenticated user no longer exists.", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void changePassword(
            @AuthenticationPrincipal UUID userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                description = "Current password and a new password containing 8 to 72 characters.",
                content = @Content(
                    schema = @Schema(implementation = ChangePasswordRequest.class),
                    examples = @ExampleObject(value = "{\"oldPassword\":\"correct-horse-demo\",\"newPassword\":\"new-correct-horse-demo\"}")
                )
            )
            @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(userId, request.oldPassword(), request.newPassword());
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete the current account", description = "Deletes the authenticated user's persisted account data. Existing Redis sessions are not explicitly revoked by this operation.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account deleted successfully; the response body is empty."),
        @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "The authenticated user no longer exists.", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void deleteAccount(@AuthenticationPrincipal UUID userId) {
        accountService.delete(userId);
    }

    @Schema(name = "MeResponse", description = "Authenticated account and virtual cash balance.")
    public record MeResponse(
        @Schema(description = "User identifier.", example = "018f3e55-7a55-7b2d-9c11-4d91b44f8b10", format = "uuid") UUID id,
        @Schema(description = "Normalized account email.", example = "trader@example.com", format = "email") String email,
        @Schema(description = "Available virtual USD balance.", example = "14950.00") BigDecimal usdBalance,
        @Schema(description = "Account creation time.", example = "2026-07-19T12:00:00Z", format = "date-time") java.time.Instant createdAt
    ) {}
    
    @Schema(name = "ChangePasswordRequest", description = "Current and replacement passwords.")
    public record ChangePasswordRequest(
        @Schema(description = "Current account password.", example = "correct-horse-demo", format = "password")
        @NotBlank String oldPassword,
        @Schema(description = "Replacement password containing 8 to 72 characters.", example = "new-correct-horse-demo", minLength = 8, maxLength = 72, format = "password")
        @NotBlank @Size(min = 8, max = 72, message = "New password must be at least 8 characters long.") String newPassword
    ) {}
}
