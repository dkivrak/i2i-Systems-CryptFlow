package com.i2i.cryptflow.shared.security;

import com.i2i.cryptflow.auth.SessionService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
  private final SessionService sessions;
  public SessionAuthenticationFilter(SessionService sessions){this.sessions=sessions;}
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
    String header=req.getHeader(HttpHeaders.AUTHORIZATION);
    if(header!=null && header.startsWith("Bearer ")){
      sessions.resolve(header.substring(7)).ifPresent(userId -> SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(userId,null,List.of())));
    }
    chain.doFilter(req,res);
  }
}

