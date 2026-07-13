package com.i2i.cryptflow.auth;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
  private static final String PREFIX="session:";
  private final StringRedisTemplate redis;
  private final Duration ttl;
  public SessionService(StringRedisTemplate redis, @Value("${app.session-ttl-hours:24}") long hours){this.redis=redis;ttl=Duration.ofHours(hours);}
  public Session create(UUID userId){var token=UUID.randomUUID();redis.opsForValue().set(PREFIX+token,userId.toString(),ttl);return new Session(token,Instant.now().plus(ttl));}
  public Optional<UUID> resolve(String token){
    try { return Optional.ofNullable(redis.opsForValue().get(PREFIX+UUID.fromString(token))).map(UUID::fromString); }
    catch(IllegalArgumentException ex){return Optional.empty();}
  }
  public void delete(String token){try{redis.delete(PREFIX+UUID.fromString(token));}catch(IllegalArgumentException ignored){}}
  public record Session(UUID token, Instant expiresAt) {}
}

