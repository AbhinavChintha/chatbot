package com.abhinav.agentic_ai_chatbot.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(
            name = "message",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String message;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ConversationMessage() {
    }

    public ConversationMessage(
            String sessionId,
            String role,
            String message) {

        this.sessionId = sessionId;
        this.role = role;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public ConversationMessage(
            String sessionId,
            String role,
            String message,
            String source,
            String type,
            Boolean success) {

        this.sessionId = sessionId;
        this.role = role;
        this.message = message;
        this.source = source;
        this.type = type;
        this.success = success;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}