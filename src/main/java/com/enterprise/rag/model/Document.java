package com.enterprise.rag.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Document entity — Represents an uploaded file (PDF, TXT).
 * Tracks processing status as the document moves through the ingestion pipeline.
 */
@Entity
@Table(name = "documents")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "chunk_count")
    @Builder.Default
    private Integer chunkCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Status status = Status.UPLOADING;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "uploaded_at")
    @Builder.Default
    private Instant uploadedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Document processing status lifecycle:
     * UPLOADING → PROCESSING → READY (success) or FAILED (error)
     */
    public enum Status {
        UPLOADING,
        PROCESSING,
        READY,
        FAILED
    }
}
