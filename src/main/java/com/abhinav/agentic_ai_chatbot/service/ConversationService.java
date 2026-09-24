package com.abhinav.agentic_ai_chatbot.service;

import com.abhinav.agentic_ai_chatbot.entity.ConversationMessage;
import com.abhinav.agentic_ai_chatbot.repository.ConversationMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationMessageRepository repository;

    public ConversationService(
            ConversationMessageRepository repository) {

        this.repository = repository;
    }

    public void saveUserMessage(
            String sessionId,
            String message) {

        ConversationMessage conversationMessage =
                new ConversationMessage(
                        sessionId,
                        "USER",
                        message
                );

        repository.save(conversationMessage);
    }

    public void saveAssistantMessage(
            String sessionId,
            String message) {

        ConversationMessage conversationMessage =
                new ConversationMessage(
                        sessionId,
                        "ASSISTANT",
                        message
                );

        repository.save(conversationMessage);
    }

    public List<ConversationMessage> getConversation(
            String sessionId) {

        return repository
                .findBySessionIdOrderByCreatedAtAsc(
                        sessionId
                );
    }

    public String buildConversationContext(
            String sessionId) {

        List<ConversationMessage> messages =
                getConversation(sessionId);

        if (messages.isEmpty()) {
            return "No previous conversation exists.";
        }

        StringBuilder context =
                new StringBuilder();

        for (ConversationMessage message : messages) {

            context
                    .append(message.getRole())
                    .append(": ")
                    .append(message.getMessage())
                    .append("\n");
        }

        return context.toString();
    }
}