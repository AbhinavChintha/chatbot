package com.abhinav.agentic_ai_chatbot.agent;

import com.abhinav.agentic_ai_chatbot.dto.SupervisorDecision;
import com.abhinav.agentic_ai_chatbot.service.SupervisorService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class SupervisorAgent {

    private final ChatClient chatClient;
    private final SupervisorService supervisorService;

    public SupervisorAgent(
            ChatClient.Builder chatClientBuilder,
            SupervisorService supervisorService) {

        this.chatClient = chatClientBuilder.build();
        this.supervisorService = supervisorService;
    }

    public String process(String message) {

        SupervisorDecision decision = classify(message);

        return supervisorService.execute(
                decision,
                message
        );
    }

    private SupervisorDecision classify(String message) {

        String systemPrompt = """
                You are the Supervisor Agent for an enterprise AI chatbot.

                Analyze the user's request and determine which capability
                should handle it.

                Allowed intents:

                DATABASE
                RAG
                WEB_SEARCH
                GENERAL

                Allowed entities:

                EMPLOYEE
                DEVICE
                CUSTOMER
                DOCUMENT
                NONE

                Rules:

                DATABASE:
                Use when the user asks for structured enterprise data
                stored in the database.

                RAG:
                Use for internal company policies, procedures,
                documentation, manuals, or uploaded company documents.

                WEB_SEARCH:
                Use when current or real-time external information
                is required.

                GENERAL:
                Use for general knowledge, explanations, coding questions,
                or casual conversation.

                If the user asks about an employee and provides an employee ID,
                set entity to EMPLOYEE and employeeId to that ID.

                If there is no employee ID, employeeId must be null.

                Return only the structured decision.
                """;

        return chatClient
                .prompt()
                .system(systemPrompt)
                .user(message)
                .call()
                .entity(SupervisorDecision.class);
    }
}