package com.abhinav.agentic_ai_chatbot.service;

import com.abhinav.agentic_ai_chatbot.dto.ConversationSummary;
import com.abhinav.agentic_ai_chatbot.entity.ConversationMessage;
import com.abhinav.agentic_ai_chatbot.repository.ConversationMessageRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationService {

    private static final int MAX_CONTEXT_MESSAGES = 10;
    private static final int MAX_CONTEXT_CHARS = 6_000;
    private static final int MAX_CONTEXT_MESSAGE_CHARS = 1_000;

    private final ConversationMessageRepository repository;
    private final ConversationQuota quota;

    public ConversationService(
            ConversationMessageRepository repository,
            ConversationQuota quota) {
        this.repository = repository;
        this.quota = quota;
    }

    public int maxQuestions() {
        return quota.maxQuestions();
    }

    @Transactional
    public void saveUserMessage(
            String ownerUsername,
            String sessionId,
            String message) {

        quota.consume(ownerUsername, sessionId);
        ConversationMessage conversationMessage =
                new ConversationMessage(
                        ownerUsername,
                        sessionId,
                        "USER",
                        message
                );

        repository.save(conversationMessage);
    }

    public void saveAssistantMessage(
            String ownerUsername,
            String sessionId,
            String message,
            String source,
            String type,
            boolean success) {

        ConversationMessage conversationMessage =
                new ConversationMessage(
                        ownerUsername,
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
            String ownerUsername,
            String sessionId) {

        return repository
                .findByOwnerUsernameAndSessionIdOrderByCreatedAtAsc(
                        ownerUsername,
                        sessionId
                );
    }

    public String buildConversationContext(
            String ownerUsername,
            String sessionId) {

        List<ConversationMessage> recentMessages =
                repository.findTop10ByOwnerUsernameAndSessionIdOrderByIdDesc(
                        ownerUsername,
                        sessionId
                );

        if (recentMessages.isEmpty()) {
            return "No previous conversation exists.";
        }

        List<String> boundedMessages = new ArrayList<>();
        int chars = 0;
        for (ConversationMessage message : recentMessages.stream().limit(MAX_CONTEXT_MESSAGES).toList()) {
            String text = message.getMessage();
            if (text.length() > MAX_CONTEXT_MESSAGE_CHARS) {
                text = text.substring(0, MAX_CONTEXT_MESSAGE_CHARS) + "...";
            }
            String entry = message.getRole() + ": " + text + "\n";
            if (chars + entry.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            boundedMessages.add(entry);
            chars += entry.length();
        }
        Collections.reverse(boundedMessages);
        return String.join("", boundedMessages);
    }

    /**
     * Returns a previously successful answer for the same
     * question in the same session, if one exists.
     */
    public ConversationMessage findCachedAnswer(
            String ownerUsername,
            String sessionId,
            String question) {

        List<ConversationMessage> cachedAnswers =
                repository.findCachedAnswers(
                        ownerUsername,
                        sessionId,
                        question,
                        PageRequest.of(0, 1)
                );

        if (cachedAnswers.isEmpty()) {
            return null;
        }

        return cachedAnswers.get(0);
    }

    public List<ConversationSummary> getConversationSummaries(
            String ownerUsername) {

        List<ConversationMessage> messages =
                repository.findByOwnerUsernameOrderByCreatedAtAsc(
                        ownerUsername
                );

        Map<String, List<ConversationMessage>> grouped =
                new LinkedHashMap<>();

        for (ConversationMessage message : messages) {
            grouped.computeIfAbsent(
                    message.getSessionId(),
                    key -> new ArrayList<>()
            ).add(message);
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
                    buildConversationTitle(sessionMessages);

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

        summaries.sort(
                (first, second) ->
                        second.getUpdatedAt()
                                .compareTo(first.getUpdatedAt())
        );

        return summaries;
    }

    private String buildConversationTitle(
            List<ConversationMessage> messages) {

        for (ConversationMessage message : messages) {

            if ("USER".equalsIgnoreCase(message.getRole())) {

                String text = message.getMessage();

                if (text == null || text.isBlank()) {
                    return "New Conversation";
                }

                text = text.trim();

                if (text.length() > 50) {
                    return text.substring(0, 50) + "...";
                }

                return text;
            }
        }

        return "New Conversation";
    }
}