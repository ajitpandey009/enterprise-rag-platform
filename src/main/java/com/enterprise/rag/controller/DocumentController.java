package com.enterprise.rag.controller;

import com.enterprise.rag.dto.DocumentDto;
import com.enterprise.rag.exception.GlobalExceptionHandler.ResourceNotFoundException;
import com.enterprise.rag.ingestion.DocumentIngestionService;
import com.enterprise.rag.model.Document;
import com.enterprise.rag.model.Tenant;
import com.enterprise.rag.model.User;
import com.enterprise.rag.observability.AuditService;
import com.enterprise.rag.repository.DocumentChunkRepository;
import com.enterprise.rag.repository.DocumentRepository;
import com.enterprise.rag.security.TenantContext;
import com.enterprise.rag.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Document upload and management")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentIngestionService ingestionService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public DocumentController(DocumentRepository documentRepository,
                               DocumentChunkRepository chunkRepository,
                               DocumentIngestionService ingestionService,
                               AuditService auditService,
                               JdbcTemplate jdbcTemplate) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ingestionService = ingestionService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document for RAG ingestion")
    public ResponseEntity<DocumentDto.DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("Upload request: {} ({} bytes) by user {}",
                file.getOriginalFilename(), file.getSize(), principal.getUsername());

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/pdf")
                && !contentType.equals("text/plain")
                && !contentType.startsWith("application/"))){
            return ResponseEntity.badRequest().build();
        }

        // Create document record
        Tenant tenantRef = new Tenant();
        tenantRef.setId(principal.getTenantId());
        User userRef = new User();
        userRef.setId(principal.getId());

        Document document = Document.builder()
                .tenant(tenantRef)
                .user(userRef)
                .filename(UUID.randomUUID() + "_" + file.getOriginalFilename())
                .originalFilename(file.getOriginalFilename())
                .contentType(contentType)
                .fileSize(file.getSize())
                .status(Document.Status.UPLOADING)
                .build();

        document = documentRepository.save(document);

        // Read file bytes synchronously before async handoff
        // (MultipartFile temp files are deleted after the HTTP request completes)
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }

        // Kick off async processing with the byte array
        ingestionService.processDocument(document.getId(), fileBytes, file.getOriginalFilename());

        auditService.logAction("DOCUMENT_UPLOADED", "DOCUMENT", document.getId(),
                Map.of("filename", file.getOriginalFilename(), "size", file.getSize()));

        return ResponseEntity.ok(DocumentDto.fromEntity(document));
    }

    @GetMapping
    @Operation(summary = "List all documents for the current user")
    public ResponseEntity<DocumentDto.DocumentListResponse> listDocuments(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<Document> docs = documentRepository.findByUserIdAndTenantIdOrderByCreatedAtDesc(
                principal.getId(), principal.getTenantId());
        List<DocumentDto.DocumentResponse> responses = docs.stream()
                .map(DocumentDto::fromEntity).toList();
        return ResponseEntity.ok(DocumentDto.DocumentListResponse.builder()
                .documents(responses).totalCount(responses.size()).build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document details")
    public ResponseEntity<DocumentDto.DocumentResponse> getDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Document doc = documentRepository.findByIdAndTenantId(id, principal.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
        return ResponseEntity.ok(DocumentDto.fromEntity(doc));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document and its chunks")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Document doc = documentRepository.findByIdAndTenantId(id, principal.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));

        // 1. Delete chunk metadata
        chunkRepository.deleteByDocumentId(doc.getId());
        
        // 2. Delete vector embeddings from pgvector (LangChain4j stores these separately)
        String deleteEmbeddingsSql = "DELETE FROM document_embeddings WHERE metadata->>'documentId' = ?";
        jdbcTemplate.update(deleteEmbeddingsSql, doc.getId().toString());

        // 3. Delete document record
        documentRepository.delete(doc);

        auditService.logAction("DOCUMENT_DELETED", "DOCUMENT", id,
                Map.of("filename", doc.getOriginalFilename()));

        return ResponseEntity.noContent().build();
    }
}
