package com.enterprise.rag.repository;

import com.enterprise.rag.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    /** Find all chunks for a document, ordered by index */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(UUID documentId);

    /** Delete all chunks for a document (cascade cleanup) */
    @Modifying
    void deleteByDocumentId(UUID documentId);

    /** Count chunks for a document */
    long countByDocumentId(UUID documentId);

    /**
     * Semantic similarity search using pgvector.
     * Finds the most similar chunks to a query embedding vector
     * within a specific tenant's documents.
     *
     * Uses cosine distance operator (<=>) for similarity ranking.
     * Lower distance = higher similarity.
     */
    @Query(value = """
        SELECT dc.id, dc.document_id, dc.tenant_id, dc.chunk_index,
               dc.content, dc.token_count, dc.created_at,
               1 - (dc.embedding <=> cast(:queryEmbedding AS vector)) as similarity_score
        FROM document_chunks dc
        WHERE dc.tenant_id = :tenantId
          AND dc.embedding IS NOT NULL
        ORDER BY dc.embedding <=> cast(:queryEmbedding AS vector)
        LIMIT :maxResults
        """, nativeQuery = true)
    List<Object[]> findSimilarChunks(UUID tenantId, String queryEmbedding, int maxResults);
}
