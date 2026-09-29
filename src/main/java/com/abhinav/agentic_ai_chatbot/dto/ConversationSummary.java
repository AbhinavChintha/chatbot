package com.abhinav.agentic_ai_chatbot.dto;

import java.time.LocalDateTime;

public class ConversationSummary {

    private String sessionId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int messageCount;

    public ConversationSummary() {
    }

    public ConversationSummary(
            String sessionId,
            String title,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            int messageCount) {

        this.sessionId = sessionId;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messageCount = messageCount;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
}