package com.abhinav.agentic_ai_chatbot.controller;

import com.abhinav.agentic_ai_chatbot.dto.ConversationSummary;
import com.abhinav.agentic_ai_chatbot.entity.ConversationMessage;
import com.abhinav.agentic_ai_chatbot.service.ConversationService;
import com.abhinav.agentic_ai_chatbot.service.ConversationSessionService;
import com.abhinav.agentic_ai_chatbot.dto.CreateConversationRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@Tag(
        name = "Conversation History",
        description = "APIs for retrieving persisted chatbot conversations"
)
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationSessionService sessions;

    public ConversationController(
            ConversationService conversationService,
            ConversationSessionService sessions) {

        this.conversationService =
                conversationService;
        this.sessions = sessions;
    }

    public record CreatedConversation(String sessionId) { }

    @PostMapping
    @Operation(summary = "Create a conversation idempotently",
            description = "Reuse the same creationKey when retrying. Keys are scoped to the authenticated owner.")
    public CreatedConversation create(
            @Valid @RequestBody CreateConversationRequest request, Authentication authentication) {
        return new CreatedConversation(sessions.create(
                authentication.getName(), UUID.fromString(request.creationKey())));
    }

    @Operation(
            summary = "Get all conversations",
            description =
                    "Returns all persisted conversation sessions, ordered by most recently updated first."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description =
                            "Conversations retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description =
                            "Internal server error"
            )
    })
    @GetMapping
    public ResponseEntity<
            List<ConversationSummary>>
    getConversations(Authentication authentication) {

        return ResponseEntity.ok(
                conversationService
                        .getConversationSummaries(
                                authentication.getName()
                        )
        );
    }

    @Operation(
            summary = "Get conversation messages",
            description =
                    "Returns all messages for a session, including persisted response source metadata when available."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description =
                            "Conversation retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description =
                            "Conversation not found"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description =
                            "Internal server error"
            )
    })
    @GetMapping("/{sessionId}")
    public ResponseEntity<
            List<ConversationMessage>>
    getConversation(
            @PathVariable String sessionId,
            Authentication authentication) {

        List<ConversationMessage> messages =
                conversationService
                        .getConversation(
                                authentication.getName(),
                                sessionId
                        );

        if (messages.isEmpty()) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity.ok(messages);
    }
}