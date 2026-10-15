
package com.abhinav.agentic_ai_chatbot.repository;

import com.abhinav.agentic_ai_chatbot.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ConversationMessageRepository
        extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findBySessionIdOrderByCreatedAtAsc(
            String sessionId
    );

    // Fetch only the latest 10 messages for AI context.
    List<ConversationMessage> findTop10BySessionIdOrderByIdDesc(
            String sessionId
    );

    List<ConversationMessage> findAllByOrderByCreatedAtAsc();

    @Query("""
    SELECT a
    FROM ConversationMessage a
    WHERE a.sessionId = :sessionId
      AND a.role = 'ASSISTANT'
      AND a.success = true
      AND EXISTS (
          SELECT u.id
          FROM ConversationMessage u
          WHERE u.sessionId = :sessionId
            AND u.role = 'USER'
            AND u.message = :question
            AND u.id < a.id
            AND NOT EXISTS (
                SELECT u2.id
                FROM ConversationMessage u2
                WHERE u2.sessionId = :sessionId
                  AND u2.role = 'USER'
                  AND u2.id > u.id
                  AND u2.id < a.id
            )
      )
    ORDER BY a.id DESC
    """)
    List<ConversationMessage> findCachedAnswers(
            @Param("sessionId") String sessionId,
            @Param("question") String question
    );

}
