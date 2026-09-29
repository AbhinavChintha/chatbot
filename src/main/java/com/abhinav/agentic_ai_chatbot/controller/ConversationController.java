package com.abhinav.agentic_ai_chatbot.controller;

import com.abhinav.agentic_ai_chatbot.dto.ConversationSummary;
import com.abhinav.agentic_ai_chatbot.entity.ConversationMessage;
import com.abhinav.agentic_ai_chatbot.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(
        name = "Conversation History",
        description = "APIs for retrieving persisted chatbot conversations"
)
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(
            ConversationService conversationService) {

        this.conversationService =
                conversationService;
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
    getConversations() {

        return ResponseEntity.ok(
                conversationService
                        .getConversationSummaries()
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
            @PathVariable String sessionId) {

        List<ConversationMessage> messages =
                conversationService
                        .getConversation(sessionId);

        if (messages.isEmpty()) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity.ok(messages);
    }
}