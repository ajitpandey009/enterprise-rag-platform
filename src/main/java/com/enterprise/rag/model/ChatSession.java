package com.enterprise.rag.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * ChatSession — Groups related chat messages into a conversation.
 * Each session belongs to a user within a tenant.
 */
@Entity
@Table(name = "chat_sessions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 500)
    @Builder.Default
    private String title = "New Chat";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "message_count")
    @Builder.Default
    private Integer messageCount = 0;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
