package com.abhinav.agentic_ai_chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "Natural-language request sent to the Agentic AI chatbot"
)
public class ChatRequest {

    @Schema(
            description = "User's natural-language question or request",
            example = "Show me employee 101",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String message;

    public ChatRequest() {
    }

    public ChatRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}