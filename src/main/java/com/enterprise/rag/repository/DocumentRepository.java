package com.enterprise.rag.repository;

import com.enterprise.rag.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** Find all documents belonging to a specific tenant */
    List<Document> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** Find all documents uploaded by a specific user */
    List<Document> findByUserIdAndTenantIdOrderByCreatedAtDesc(UUID userId, UUID tenantId);

    /** Find document by ID within a tenant (security check) */
    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Count documents by status for a tenant */
    @Query("SELECT COUNT(d) FROM Document d WHERE d.tenant.id = :tenantId AND d.status = :status")
    long countByTenantIdAndStatus(UUID tenantId, Document.Status status);

    /** Count total documents for a tenant */
    long countByTenantId(UUID tenantId);
}
