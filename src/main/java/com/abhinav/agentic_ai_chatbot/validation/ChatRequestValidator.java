package com.abhinav.agentic_ai_chatbot.validation;

import com.abhinav.agentic_ai_chatbot.dto.ChatRequest;
import com.abhinav.agentic_ai_chatbot.exception.InvalidRequestException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class ChatRequestValidator {

    private final int maxMessageChars;

    public ChatRequestValidator(@Value("${app.chat.max-message-chars:2000}") int maxMessageChars) {
        if (maxMessageChars < 1) {
            throw new IllegalArgumentException("Message length limit must be positive.");
        }
        this.maxMessageChars = maxMessageChars;
    }

    public int maxMessageChars() {
        return maxMessageChars;
    }

    public void validate(ChatRequest request) {

        if (request == null) {

            throw new InvalidRequestException(
                    "Request body cannot be empty."
            );
        }

        if (request.getSessionId() == null
                || request.getSessionId().trim().isEmpty()) {

            throw new InvalidRequestException(
                    "Session ID cannot be empty."
            );
        }

        if (request.getMessage() == null
                || request.getMessage().trim().isEmpty()) {

            throw new InvalidRequestException(
                    "Message cannot be empty."
            );
        }
        if (request.getSessionId().length() > 100
                || request.getSessionId().chars().anyMatch(Character::isISOControl)) {
            throw new InvalidRequestException("Session ID must be at most 100 characters without control characters.");
        }
        if (request.getMessage().length() > maxMessageChars) {
            throw new InvalidRequestException(
                    "Message must be at most " + maxMessageChars + " characters.");
        }
    }
}