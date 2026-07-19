package com.i2i.cryptflow.chat;

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
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Authenticated Gemini assistance enriched with account, portfolio, trade, and market context.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ChatController {
  private static final int MAX_MESSAGE_LENGTH = 2000;

  private final ChatService service;

  public ChatController(ChatService service) {
    this.service = service;
  }

  @PostMapping("/query")
  @Operation(summary = "Ask the CryptFlow assistant", description = "Sends an account-aware educational crypto prompt to Gemini. The backend ensures the configured educational disclaimer is present in the answer.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Gemini response with a separate disclaimer field.",
          content = @Content(
              schema = @Schema(implementation = ChatService.ChatResponse.class),
              examples = @ExampleObject(value = "{\"answer\":\"Your portfolio is concentrated in BTC. Educational purposes only — not financial advice.\",\"disclaimer\":\"Educational purposes only — not financial advice.\",\"generatedAt\":\"2026-07-19T12:00:00Z\"}")
          )
      ),
      @ApiResponse(responseCode = "400", description = "Malformed body or a blank/message-over-2000-characters validation failure.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "401", description = "Bearer token is missing, malformed, expired, or invalid.", content = @Content(schema = @Schema(implementation = ApiError.class))),
      @ApiResponse(responseCode = "503", description = "Gemini configuration is missing or the Gemini service is unavailable.", content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  ChatService.ChatResponse query(
      @AuthenticationPrincipal UUID userId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true,
          description = "Educational crypto, account, portfolio, trade, or market question.",
          content = @Content(
              schema = @Schema(implementation = ChatRequest.class),
              examples = @ExampleObject(value = "{\"message\":\"How concentrated is my portfolio?\"}")
          )
      )
      @Valid @RequestBody ChatRequest r) {
    return service.query(userId, r.message());
  }

  @Schema(name = "ChatRequest", description = "Question for the account-aware educational assistant.")
  public record ChatRequest(
      @Schema(description = "Nonblank question of at most 2000 characters.", example = "How concentrated is my portfolio?", minLength = 1, maxLength = MAX_MESSAGE_LENGTH)
      @NotBlank @Size(max = MAX_MESSAGE_LENGTH) String message
  ) {}
}
