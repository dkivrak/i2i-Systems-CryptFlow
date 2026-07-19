package com.i2i.cryptflow.auth;

import com.i2i.cryptflow.shared.config.OpenApiConfig;
import com.i2i.cryptflow.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Create accounts and manage Redis-backed login sessions.")
public class AuthController {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthService auth;
  private final SessionService sessions;

  public AuthController(AuthService auth, SessionService sessions) {
    this.auth = auth;
    this.sessions = sessions;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register an account", description = "Creates a user and wallet with a generated virtual USD balance. No Gemini API key is required.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Account created.",
          content = @Content(
              schema = @Schema(implementation = AuthService.RegisterResult.class),
              examples = @ExampleObject(value = "{\"userId\":\"018f3e55-7a55-7b2d-9c11-4d91b44f8b10\",\"email\":\"trader@example.com\",\"initialBalance\":15000.00}")
          )
      ),
      @ApiResponse(responseCode = "400", description = "Malformed body or email/password validation failure.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "409", description = "The normalized email address is already registered.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  AuthService.RegisterResult register(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "Registration credentials. Passwords must contain 8 to 72 characters.",
          content = @Content(
              schema = @Schema(implementation = RegisterRequest.class),
              examples = @ExampleObject(value = "{\"email\":\"trader@example.com\",\"password\":\"correct-horse-demo\"}")
          )
      )
      @Valid @RequestBody RegisterRequest r) {
    return auth.register(r.email(), r.password());
  }

  @PostMapping("/login")
  @Operation(summary = "Log in", description = "Returns an opaque UUID session token. Supply that token through Swagger UI's Authorize dialog for protected operations.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Login successful.",
          content = @Content(
              schema = @Schema(implementation = AuthService.LoginResult.class),
              examples = @ExampleObject(value = "{\"token\":\"123e4567-e89b-12d3-a456-426614174000\",\"expiresAt\":\"2026-07-20T12:00:00Z\",\"userId\":\"018f3e55-7a55-7b2d-9c11-4d91b44f8b10\",\"email\":\"trader@example.com\"}")
          )
      ),
      @ApiResponse(responseCode = "400", description = "Malformed body or email/password validation failure.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "401", description = "Email or password is invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  AuthService.LoginResult login(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "Account credentials.",
          content = @Content(
              schema = @Schema(implementation = LoginRequest.class),
              examples = @ExampleObject(value = "{\"email\":\"trader@example.com\",\"password\":\"correct-horse-demo\"}")
          )
      )
      @Valid @RequestBody LoginRequest r) {
    return auth.login(r.email(), r.password());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Log out", description = "Deletes the current opaque session token from Redis.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Session deleted."),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  void logout(
      @Parameter(hidden = true)
      @RequestHeader("Authorization") String header) {
    sessions.delete(header.substring(BEARER_PREFIX.length()));
  }

  @Schema(name = "RegisterRequest", description = "New CryptFlow account credentials.")
  public record RegisterRequest(
      @Schema(description = "Email address; normalized to lowercase before storage.", example = "trader@example.com", format = "email")
      @NotBlank @Email String email,
      @Schema(description = "Password containing 8 to 72 characters.", example = "correct-horse-demo", minLength = 8, maxLength = 72, format = "password")
      @NotBlank @Size(min = 8, max = 72) String password) {}

  @Schema(name = "LoginRequest", description = "Existing account credentials.")
  public record LoginRequest(
      @Schema(description = "Registered email address.", example = "trader@example.com", format = "email")
      @NotBlank @Email String email,
      @Schema(description = "Account password.", example = "correct-horse-demo", format = "password")
      @NotBlank String password) {}
}
