package com.abhinav.agentic_ai_chatbot.controller;

import com.abhinav.agentic_ai_chatbot.agent.SupervisorAgent;
import com.abhinav.agentic_ai_chatbot.dto.ChatRequest;
import com.abhinav.agentic_ai_chatbot.dto.ChatResponse;
import com.abhinav.agentic_ai_chatbot.validation.ChatRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Tag(
        name = "Enterprise Chatbot",
        description = "Agentic AI chatbot API that routes requests to Database, RAG, Web Search, or General LLM"
)
public class ChatController {

    private final SupervisorAgent supervisorAgent;
    private final ChatRequestValidator chatRequestValidator;

    public ChatController(
            SupervisorAgent supervisorAgent,
            ChatRequestValidator chatRequestValidator) {

        this.supervisorAgent = supervisorAgent;
        this.chatRequestValidator = chatRequestValidator;
    }

    @Operation(
            summary = "Process a chatbot request",
            description = """
                    Accepts a natural-language user request and routes it
                    through the Supervisor Agent.

                    Supported routes:

                    DATABASE
                    Employee information retrieved from MySQL.

                    RAG
                    Internal company documents and policies.

                    WEB_SEARCH
                    Current external information from the internet.

                    GENERAL
                    General-purpose LLM questions.
                    """,
            requestBody = @RequestBody(
                    description = "Natural-language request sent to the chatbot",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ChatRequest.class
                            ),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "message": "Show me employee 101"
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({

            @ApiResponse(
                    responseCode = "200",
                    description = "Request processed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ChatResponse.class
                            ),
                            examples = {

                                    @ExampleObject(
                                            name = "Database Response",
                                            value = """
                                                    {
                                                      "answer": "Employee details retrieved successfully.",
                                                      "source": "Employee Database",
                                                      "type": "DATABASE",
                                                      "success": true,
                                                      "data": {
                                                        "id": 101,
                                                        "name": "Rahul Sharma",
                                                        "department": "Engineering",
                                                        "role": "Java Developer",
                                                        "email": "rahul@example.com",
                                                        "location": "Hyderabad"
                                                      }
                                                    }
                                                    """
                                    ),

                                    @ExampleObject(
                                            name = "RAG Response",
                                            value = """
                                                    {
                                                      "answer": "Employees are entitled to 20 days of annual leave per calendar year.",
                                                      "source": "Company Leave Policy",
                                                      "type": "RAG",
                                                      "success": true,
                                                      "data": {
                                                        "documents": [
                                                          "leave-policy.txt"
                                                        ]
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
            ),

            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ChatResponse.class
                            ),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "answer": "Message cannot be empty.",
                                              "source": "Chat API",
                                              "type": "VALIDATION_ERROR",
                                              "success": false,
                                              "data": null
                                            }
                                            """
                            )
                    )
            ),

            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ChatResponse.class
                            ),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "answer": "An unexpected error occurred while processing your request.",
                                              "source": "Chat API",
                                              "type": "INTERNAL_ERROR",
                                              "success": false,
                                              "data": null
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @org.springframework.web.bind.annotation.RequestBody(
                    required = false
            )
            ChatRequest request) {

        chatRequestValidator.validate(request);

        return ResponseEntity.ok(
                supervisorAgent.process(
                        request.getMessage().trim()
                )
        );
    }
}