package com.enterprise.rag.controller;

import com.enterprise.rag.model.AuditLog;
import com.enterprise.rag.repository.AuditLogRepository;
import com.enterprise.rag.repository.DocumentRepository;
import com.enterprise.rag.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin-only platform management")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditLogRepository auditLogRepository;
    private final DocumentRepository documentRepository;

    public AdminController(AuditLogRepository auditLogRepository,
                            DocumentRepository documentRepository) {
        this.auditLogRepository = auditLogRepository;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "View audit logs (paginated)")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(
                principal.getTenantId(), PageRequest.of(page, size));
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/stats")
    @Operation(summary = "Platform statistics")
    public ResponseEntity<Map<String, Object>> getStats(
            @AuthenticationPrincipal UserPrincipal principal) {
        long totalDocs = documentRepository.countByTenantId(principal.getTenantId());
        long readyDocs = documentRepository.countByTenantIdAndStatus(
                principal.getTenantId(), com.enterprise.rag.model.Document.Status.READY);
        return ResponseEntity.ok(Map.of(
                "totalDocuments", totalDocs,
                "readyDocuments", readyDocs,
                "processingDocuments", totalDocs - readyDocs
        ));
    }
}
