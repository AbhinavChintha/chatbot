package com.abhinav.agentic_ai_chatbot.repository;

import com.abhinav.agentic_ai_chatbot.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMessageRepository
        extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(
            String sessionId
    );

    List<ConversationMessage> findAllByOrderByCreatedAtAsc();
}