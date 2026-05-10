package com.enterprise.rag.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * ChatMessage — Individual message within a chat session.
 * Stores role (USER/ASSISTANT/SYSTEM), content, token usage,
 * and references to source chunks used for RAG context.
 */
@Entity
@Table(name = "chat_messages")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    /** JSON array of chunk IDs used as RAG context */
    @Column(name = "source_chunks", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String sourceChunks;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum MessageRole {
        USER,
        ASSISTANT,
        SYSTEM
    }
}
