package com.abhinav.agentic_ai_chatbot.validation;

import com.abhinav.agentic_ai_chatbot.dto.ChatRequest;
import com.abhinav.agentic_ai_chatbot.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

@Component
public class ChatRequestValidator {

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
    }
}