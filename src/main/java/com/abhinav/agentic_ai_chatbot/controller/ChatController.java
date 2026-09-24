package com.abhinav.agentic_ai_chatbot.controller;

import com.abhinav.agentic_ai_chatbot.agent.SupervisorAgent;
import com.abhinav.agentic_ai_chatbot.dto.ChatRequest;
import com.abhinav.agentic_ai_chatbot.dto.ChatResponse;
import com.abhinav.agentic_ai_chatbot.service.ConversationService;
import com.abhinav.agentic_ai_chatbot.validation.ChatRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Tag(
        name = "Enterprise Chatbot",
        description = "Agentic AI chatbot that routes requests to Database, RAG, Web Search, or General LLM"
)
public class ChatController {

    private final SupervisorAgent supervisorAgent;
    private final ChatRequestValidator chatRequestValidator;
    private final ConversationService conversationService;

    public ChatController(
            SupervisorAgent supervisorAgent,
            ChatRequestValidator chatRequestValidator,
            ConversationService conversationService) {

        this.supervisorAgent = supervisorAgent;
        this.chatRequestValidator = chatRequestValidator;
        this.conversationService = conversationService;
    }

    @Operation(
            summary = "Chat with Enterprise AI Assistant",
            description = """
                    Sends a user message to the Agentic AI Supervisor.

                    The Supervisor dynamically routes the request to:
                    Database, RAG, Web Search, General LLM,
                    or multiple tools.

                    The sessionId is used to maintain conversation history.
                    """
    )
    @ApiResponses({

            @ApiResponse(
                    responseCode = "200",
                    description = "Chat response generated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ChatResponse.class
                            )
                    )
            ),

            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "error": "Session ID cannot be empty."
                                            }
                                            """
                            )
                    )
            ),

            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @RequestBody(required = false)
            ChatRequest request) {

        /*
         * Validate request.
         */
        chatRequestValidator.validate(request);

        String sessionId =
                request.getSessionId().trim();

        String message =
                request.getMessage().trim();

        /*
         * Load previous conversation BEFORE
         * saving the current user message.
         */
        String conversationContext =
                conversationService.buildConversationContext(
                        sessionId
                );

        /*
         * Save current user message.
         */
        conversationService.saveUserMessage(
                sessionId,
                message
        );

        /*
         * Send current message + previous conversation
         * to Supervisor Agent.
         */
        ChatResponse response =
                supervisorAgent.process(
                        message,
                        conversationContext
                );

        /*
         * Save assistant response.
         */
        conversationService.saveAssistantMessage(
                sessionId,
                response.getAnswer()
        );

        return ResponseEntity.ok(response);
    }
}