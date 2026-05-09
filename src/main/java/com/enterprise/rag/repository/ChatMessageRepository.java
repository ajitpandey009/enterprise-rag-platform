package com.enterprise.rag.repository;

import com.enterprise.rag.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Get all messages for a session, ordered chronologically */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /** Get the N most recent messages for a session (for chat memory window) */
    List<ChatMessage> findTop20BySessionIdOrderByCreatedAtDesc(UUID sessionId);

    /** Count messages in a session */
    long countBySessionId(UUID sessionId);
}
