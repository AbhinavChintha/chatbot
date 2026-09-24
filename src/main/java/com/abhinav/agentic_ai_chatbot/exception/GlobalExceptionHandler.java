package com.abhinav.agentic_ai_chatbot.exception;

import com.abhinav.agentic_ai_chatbot.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ChatResponse> handleInvalidRequest(
            InvalidRequestException exception) {

        logger.warn(
                "Invalid chat request: {}",
                exception.getMessage()
        );

        ChatResponse response =
                new ChatResponse(
                        exception.getMessage(),
                        "Chat API",
                        "VALIDATION_ERROR",
                        false,
                        null
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ChatResponse> handleUnexpectedException(
            Exception exception) {

        logger.error(
                "Unexpected error while processing chat request",
                exception
        );

        ChatResponse response =
                new ChatResponse(
                        "An unexpected error occurred while processing your request.",
                        "Chat API",
                        "INTERNAL_ERROR",
                        false,
                        null
                );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}