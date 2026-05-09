package com.enterprise.rag.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * AuditLog — Immutable audit trail for all significant platform actions.
 * Captures who did what, when, from where, and whether it succeeded.
 */
@Entity
@Table(name = "audit_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 100)
    private String username;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    /** Structured details about the action (stored as JSONB) */
    @Column(columnDefinition = "JSONB")
    private String details;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(length = 20)
    @Builder.Default
    private String status = "SUCCESS";

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
