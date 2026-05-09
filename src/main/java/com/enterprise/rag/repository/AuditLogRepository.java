package com.enterprise.rag.repository;

import com.enterprise.rag.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** Paginated audit logs for a tenant, newest first */
    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /** Filter audit logs by action type */
    Page<AuditLog> findByTenantIdAndAction(UUID tenantId, String action, Pageable pageable);

    /** Audit logs within a time range */
    Page<AuditLog> findByTenantIdAndCreatedAtBetween(
            UUID tenantId, Instant start, Instant end, Pageable pageable);
}
