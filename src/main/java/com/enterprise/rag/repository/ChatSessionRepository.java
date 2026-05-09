package com.enterprise.rag.repository;

import com.enterprise.rag.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    /** List all sessions for a user within a tenant, newest first */
    List<ChatSession> findByUserIdAndTenantIdOrderByUpdatedAtDesc(UUID userId, UUID tenantId);

    /** Find session by ID with tenant isolation */
    Optional<ChatSession> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Count active sessions for a user */
    long countByUserIdAndIsActive(UUID userId, Boolean isActive);
}
