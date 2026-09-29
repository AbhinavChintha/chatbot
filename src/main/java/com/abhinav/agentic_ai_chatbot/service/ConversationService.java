package com.abhinav.agentic_ai_chatbot.service;

import com.abhinav.agentic_ai_chatbot.dto.ConversationSummary;
import com.abhinav.agentic_ai_chatbot.entity.ConversationMessage;
import com.abhinav.agentic_ai_chatbot.repository.ConversationMessageRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            String message,
            String source,
            String type,
            boolean success) {

        ConversationMessage conversationMessage =
                new ConversationMessage(
                        sessionId,
                        "ASSISTANT",
                        message,
                        source,
                        type,
                        success
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

    /*
     * Returns a summary of every conversation session.
     */
    public List<ConversationSummary> getConversationSummaries() {

        List<ConversationMessage> messages =
                repository.findAllByOrderByCreatedAtAsc();

        Map<String, List<ConversationMessage>> grouped =
                new LinkedHashMap<>();

        for (ConversationMessage message : messages) {

            grouped
                    .computeIfAbsent(
                            message.getSessionId(),
                            key -> new ArrayList<>()
                    )
                    .add(message);
        }

        List<ConversationSummary> summaries =
                new ArrayList<>();

        for (Map.Entry<String, List<ConversationMessage>> entry
                : grouped.entrySet()) {

            String sessionId = entry.getKey();

            List<ConversationMessage> sessionMessages =
                    entry.getValue();

            if (sessionMessages.isEmpty()) {
                continue;
            }

            ConversationMessage firstMessage =
                    sessionMessages.get(0);

            ConversationMessage lastMessage =
                    sessionMessages.get(
                            sessionMessages.size() - 1
                    );

            String title =
                    buildConversationTitle(
                            sessionMessages
                    );

            summaries.add(
                    new ConversationSummary(
                            sessionId,
                            title,
                            firstMessage.getCreatedAt(),
                            lastMessage.getCreatedAt(),
                            sessionMessages.size()
                    )
            );
        }

        /*
         * Most recently updated conversations should appear first.
         */
        summaries.sort(
                (first, second) ->
                        second.getUpdatedAt()
                                .compareTo(
                                        first.getUpdatedAt()
                                )
        );

        return summaries;
    }

    /*
     * Creates a simple title from the first user message.
     *
     * We intentionally do not call an LLM just to generate a title.
     */
    private String buildConversationTitle(
            List<ConversationMessage> messages) {

        for (ConversationMessage message : messages) {

            if ("USER".equalsIgnoreCase(
                    message.getRole())) {

                String text =
                        message.getMessage();

                if (text == null || text.isBlank()) {
                    return "New Conversation";
                }

                text = text.trim();

                /*
                 * Keep sidebar titles short.
                 */
                if (text.length() > 50) {
                    return text.substring(0, 50) + "...";
                }

                return text;
            }
        }

        return "New Conversation";
    }
}