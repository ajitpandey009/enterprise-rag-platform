package com.enterprise.rag.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * DocumentChunk entity — A text segment extracted from a document.
 * The embedding column stores the vector representation for similarity search.
 * Note: The actual embedding is stored in the pgvector-managed table;
 * this entity tracks the chunk metadata and content.
 */
@Entity
@Table(name = "document_chunks")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
