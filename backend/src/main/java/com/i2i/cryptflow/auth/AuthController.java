package com.i2i.cryptflow.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth; private final SessionService sessions;
  public AuthController(AuthService auth,SessionService sessions){this.auth=auth;this.sessions=sessions;}
  @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
  AuthService.RegisterResult register(@Valid @RequestBody RegisterRequest r){return auth.register(r.email(),r.password());}
  @PostMapping("/login") AuthService.LoginResult login(@Valid @RequestBody LoginRequest r){return auth.login(r.email(),r.password());}
  @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(@RequestHeader("Authorization") String header){sessions.delete(header.substring(7));}
  public record RegisterRequest(@NotBlank @Email String email,@NotBlank @Size(min=8,max=72) String password){}
  public record LoginRequest(@NotBlank @Email String email,@NotBlank String password){}
}
