
package com.abhinav.agentic_ai_chatbot.controller;

import com.abhinav.agentic_ai_chatbot.agent.SupervisorAgent;
import com.abhinav.agentic_ai_chatbot.dto.ChatRequest;
import com.abhinav.agentic_ai_chatbot.dto.ChatResponse;
import com.abhinav.agentic_ai_chatbot.service.ConversationService;
import com.abhinav.agentic_ai_chatbot.service.CurrentDateTimeService;
import com.abhinav.agentic_ai_chatbot.validation.ChatRequestValidator;
import com.abhinav.agentic_ai_chatbot.entity.ConversationMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(
        name = "Enterprise Chatbot",
        description = "Agentic AI chatbot that routes requests to Database, RAG, Web Search, or General LLM"
)
public class ChatController {

    private static final Logger log =
            LoggerFactory.getLogger(ChatController.class);

    private final SupervisorAgent supervisorAgent;
    private final ChatRequestValidator chatRequestValidator;
    private final ConversationService conversationService;
    private final CurrentDateTimeService currentDateTimeService;

    public ChatController(
            SupervisorAgent supervisorAgent,
            ChatRequestValidator chatRequestValidator,
            ConversationService conversationService,
            CurrentDateTimeService currentDateTimeService) {

        this.supervisorAgent = supervisorAgent;
        this.chatRequestValidator = chatRequestValidator;
        this.conversationService = conversationService;
        this.currentDateTimeService = currentDateTimeService;
    }

    @Operation(
            summary = "Chat with Enterprise AI Assistant",
            description = """
                    Sends a user message to the Agentic AI Supervisor.

                    The Supervisor dynamically routes the request to:
                    Database, RAG, Web Search, General LLM,
                    or multiple tools.

                    Current date/time questions can be handled locally.

                    The sessionId maintains conversation history.
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
            @RequestBody(required = false) ChatRequest request) {

        long requestStart = System.nanoTime();

        try {
            chatRequestValidator.validate(request);

            String sessionId = request.getSessionId().trim();
            String message = request.getMessage().trim();

            long stageStart = System.nanoTime();

            // Check for a previous successful answer before saving
            // the repeated question to conversation history.
            ConversationMessage cachedAnswer =
                    conversationService.findCachedAnswer(
                            sessionId,
                            message
                    );

            log.info(
                    "Chat latency | stage=checkCache | durationMs={}",
                    elapsedMs(stageStart)
            );

            if (cachedAnswer != null) {

                log.info(
                        "Response cache hit | sessionId={}",
                        sessionId
                );

                // Save this repeated question in conversation history.
                conversationService.saveUserMessage(
                        sessionId,
                        message
                );

                // Reuse the previous answer without invoking the agent.
                ChatResponse response = new ChatResponse(
                        cachedAnswer.getMessage(),
                        cachedAnswer.getSource(),
                        cachedAnswer.getType(),
                        cachedAnswer.getSuccess() != null
                                && cachedAnswer.getSuccess(),
                        null
                );

                conversationService.saveAssistantMessage(
                        sessionId,
                        response.getAnswer(),
                        response.getSource(),
                        response.getType(),
                        response.isSuccess()
                );

                return ResponseEntity.ok(response);
            }

            stageStart = System.nanoTime();

            String conversationContext =
                    conversationService.buildConversationContext(sessionId);

            log.info(
                    "Chat latency | stage=loadConversation | durationMs={}",
                    elapsedMs(stageStart)
            );

            stageStart = System.nanoTime();

            conversationService.saveUserMessage(
                    sessionId,
                    message
            );

            log.info(
                    "Chat latency | stage=saveUserMessage | durationMs={}",
                    elapsedMs(stageStart)
            );

            stageStart = System.nanoTime();

            ChatResponse response =
                    currentDateTimeService.handleIfApplicable(message);

            if (response == null) {
                response = supervisorAgent.process(
                        message,
                        conversationContext
                );
            }

            log.info(
                    "Chat latency | stage=processRequest | durationMs={}",
                    elapsedMs(stageStart)
            );

            stageStart = System.nanoTime();

            conversationService.saveAssistantMessage(
                    sessionId,
                    response.getAnswer(),
                    response.getSource(),
                    response.getType(),
                    response.isSuccess()
            );

            log.info(
                    "Chat latency | stage=saveAssistantMessage | durationMs={}",
                    elapsedMs(stageStart)
            );

            return ResponseEntity.ok(response);

        } finally {
            log.info(
                    "Chat latency | stage=totalRequest | durationMs={}",
                    elapsedMs(requestStart)
            );
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
