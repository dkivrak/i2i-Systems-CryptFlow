package com.i2i.cryptflow.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/chat")
public class ChatController {
  private final ChatService service;public ChatController(ChatService service){this.service=service;}
  @PostMapping("/query") ChatService.ChatResponse query(@AuthenticationPrincipal UUID userId,@Valid @RequestBody ChatRequest r){return service.query(userId,r.message());}
  public record ChatRequest(@NotBlank @Size(max=2000) String message){}
}
