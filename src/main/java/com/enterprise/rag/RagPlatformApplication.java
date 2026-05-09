package com.enterprise.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enterprise RAG Platform — Main Application Entry Point
 *
 * A production-grade Retrieval-Augmented Generation platform that enables
 * enterprise users to upload internal documents and ask natural language
 * questions answered by AI using only the uploaded knowledge base.
 *
 * Key capabilities:
 * - Document ingestion (PDF, TXT) with chunking and embedding
 * - Semantic similarity search via pgvector
 * - LLM-powered contextual answers (Ollama/OpenAI)
 * - Streaming responses via SSE
 * - Multi-tenant JWT-secured access
 * - Full audit trail and observability
 */
@SpringBootApplication
@EnableAsync
@EnableRetry
public class RagPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagPlatformApplication.class, args);
    }
}
