package com.enterprise.rag.dto;

import com.enterprise.rag.model.Document;
import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * DTOs for document-related API operations.
 */
public class DocumentDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DocumentResponse {
        private String id;
        private String filename;
        private String originalFilename;
        private String contentType;
        private Long fileSize;
        private Integer chunkCount;
        private String status;
        private String errorMessage;
        private Instant uploadedAt;
        private Instant processedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DocumentListResponse {
        private List<DocumentResponse> documents;
        private int totalCount;
    }

    /**
     * Map entity to response DTO.
     */
    public static DocumentResponse fromEntity(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId().toString())
                .filename(doc.getFilename())
                .originalFilename(doc.getOriginalFilename())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .chunkCount(doc.getChunkCount())
                .status(doc.getStatus().name())
                .errorMessage(doc.getErrorMessage())
                .uploadedAt(doc.getUploadedAt())
                .processedAt(doc.getProcessedAt())
                .build();
    }
}
