package com.enterprise.rag.ingestion;

import com.enterprise.rag.exception.GlobalExceptionHandler.DocumentProcessingException;
import com.enterprise.rag.model.Document;
import com.enterprise.rag.model.DocumentChunk;
import com.enterprise.rag.observability.AuditService;
import com.enterprise.rag.observability.TokenUsageTracker;
import com.enterprise.rag.repository.DocumentChunkRepository;
import com.enterprise.rag.repository.DocumentRepository;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document Ingestion Service — Handles the full pipeline from file upload
 * to searchable embeddings:
 *
 * 1. Parse document (PDF/TXT) using Apache Tika
 * 2. Split into chunks with configurable size and overlap
 * 3. Generate embeddings via LangChain4j embedding model
 * 4. Store embeddings in pgvector for similarity search
 * 5. Save chunk metadata to the document_chunks table
 *
 * Processing is asynchronous to avoid blocking the upload API.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final AuditService auditService;
    private final TokenUsageTracker tokenUsageTracker;

    @Value("${app.rag.chunk-size}")
    private int chunkSize;

    @Value("${app.rag.chunk-overlap}")
    private int chunkOverlap;

    public DocumentIngestionService(DocumentRepository documentRepository,
                                     DocumentChunkRepository chunkRepository,
                                     EmbeddingModel embeddingModel,
                                     EmbeddingStore<TextSegment> embeddingStore,
                                     AuditService auditService,
                                     TokenUsageTracker tokenUsageTracker) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.auditService = auditService;
        this.tokenUsageTracker = tokenUsageTracker;
    }

    /**
     * Process an uploaded document asynchronously.
     * This method is called after the document metadata is saved and
     * the upload API has already returned a response to the client.
     */
    @Async
    public void processDocument(UUID documentId, MultipartFile file) {
        log.info("Starting async processing for document: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentProcessingException("Document not found: " + documentId));

        try {
            // Update status to PROCESSING
            document.setStatus(Document.Status.PROCESSING);
            documentRepository.save(document);

            // Step 1: Parse document
            log.info("Parsing document: {}", document.getOriginalFilename());
            DocumentParser parser = new ApacheTikaDocumentParser();
            dev.langchain4j.data.document.Document parsedDoc;

            try (InputStream is = file.getInputStream()) {
                parsedDoc = parser.parse(is);
            }

            // Step 2: Split into chunks
            log.info("Splitting document into chunks (size={}, overlap={})", chunkSize, chunkOverlap);
            List<TextSegment> segments = DocumentSplitters
                    .recursive(chunkSize, chunkOverlap)
                    .split(parsedDoc);

            log.info("Document split into {} chunks", segments.size());

            // Step 3: Add metadata to each segment
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                segment.metadata().put("documentId", documentId.toString());
                segment.metadata().put("documentName", document.getOriginalFilename());
                segment.metadata().put("tenantId", document.getTenant().getId().toString());
                segment.metadata().put("chunkIndex", String.valueOf(i));
            }

            // Step 4: Generate embeddings and store in pgvector
            log.info("Generating embeddings for {} chunks", segments.size());
            List<Embedding> embeddings = new ArrayList<>();

            // Process in batches to avoid memory issues with large documents
            int batchSize = 10;
            for (int i = 0; i < segments.size(); i += batchSize) {
                List<TextSegment> batch = segments.subList(i, Math.min(i + batchSize, segments.size()));
                Response<List<Embedding>> response = embeddingModel.embedAll(batch);
                embeddings.addAll(response.content());
                log.debug("Embedded batch {}/{}", (i / batchSize) + 1, (segments.size() + batchSize - 1) / batchSize);
            }

            // Store in pgvector embedding store
            embeddingStore.addAll(embeddings, segments);

            // Step 5: Save chunk metadata to our table
            List<DocumentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                DocumentChunk chunk = DocumentChunk.builder()
                        .document(document)
                        .tenantId(document.getTenant().getId())
                        .chunkIndex(i)
                        .content(segments.get(i).text())
                        .tokenCount(segments.get(i).text().length() / 4)  // rough estimate
                        .build();
                chunks.add(chunk);
            }
            chunkRepository.saveAll(chunks);

            // Step 6: Update document status
            document.setStatus(Document.Status.READY);
            document.setChunkCount(segments.size());
            document.setProcessedAt(Instant.now());
            documentRepository.save(document);

            // Track usage
            tokenUsageTracker.trackEmbeddingUsage("embedding-model", embeddings.size() * 100, embeddings.size());

            // Audit
            auditService.logAction("DOCUMENT_PROCESSED", "DOCUMENT", documentId,
                    Map.of("filename", document.getOriginalFilename(),
                           "chunks", segments.size()));

            log.info("Document processed successfully: {} ({} chunks)", document.getOriginalFilename(), segments.size());

        } catch (Exception e) {
            log.error("Failed to process document {}: {}", documentId, e.getMessage(), e);

            // Mark document as failed
            document.setStatus(Document.Status.FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);

            auditService.logAction("DOCUMENT_PROCESSING_FAILED", "DOCUMENT", documentId,
                    Map.of("error", e.getMessage()));
        }
    }
}
