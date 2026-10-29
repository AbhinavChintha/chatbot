package com.abhinav.agentic_ai_chatbot.exception;

import com.abhinav.agentic_ai_chatbot.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AnswerFormatException.class)
    public ResponseEntity<ChatResponse> handleAnswerFormat(AnswerFormatException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ChatResponse(
                exception.getMessage(), "AI response", "ANSWER_FORMAT_ERROR", false, null));
    }

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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ChatResponse> handleResponseStatus(
            ResponseStatusException exception) {

        logger.warn("Request rejected with status {}", exception.getStatusCode().value());

        return ResponseEntity.status(exception.getStatusCode())
                .body(new ChatResponse(
                        exception.getReason() == null
                                ? "The request could not be processed."
                                : exception.getReason(),
                        "API",
                        "REQUEST_ERROR",
                        false,
                        null
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ChatResponse> handleValidation(
            MethodArgumentNotValidException exception) {

        logger.warn("Request body validation failed");

        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null
                        ? "Invalid request field."
                        : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));

        return ResponseEntity.badRequest().body(new ChatResponse(
                message,
                "API",
                "VALIDATION_ERROR",
                false,
                null
        ));
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