package com.i2i.cryptflow.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.i2i.cryptflow.shared.error.ApiError;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
  private static final long CORS_MAX_AGE_SECONDS = 3600L;

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, SessionAuthenticationFilter filter, ObjectMapper mapper,
      CorsConfigurationSource corsConfigurationSource) throws Exception {
    http.csrf(c -> c.disable())
      .cors(c -> c.configurationSource(corsConfigurationSource))
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a -> a
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/market/prices", "/ws/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health").permitAll()
        .anyRequest().authenticated())
      .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        res.setContentType("application/json");
        mapper.writeValue(res.getOutputStream(), new ApiError("INVALID_SESSION", "Session is invalid or has expired.", Instant.now(), List.of()));
      }))
      .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend-origins}") String origins) {
    var allowedOrigins = java.util.Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    var corsConfig = new CorsConfiguration();
    corsConfig.setAllowedOrigins(allowedOrigins);
    corsConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    corsConfig.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    corsConfig.setMaxAge(CORS_MAX_AGE_SECONDS);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", corsConfig);
    return source;
  }
}
