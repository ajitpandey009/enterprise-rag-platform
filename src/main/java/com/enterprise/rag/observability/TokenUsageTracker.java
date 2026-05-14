package com.enterprise.rag.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Token Usage Tracker — Logs and tracks LLM token consumption per request.
 * Enables cost monitoring and usage analytics for enterprise billing.
 */
@Component
public class TokenUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageTracker.class);
    
    private final MeterRegistry meterRegistry;

    public TokenUsageTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Track token usage for an AI request.
     *
     * @param modelName      The LLM model used
     * @param promptTokens   Number of tokens in the prompt
     * @param completionTokens Number of tokens in the completion
     * @param userId         The user who made the request
     */
    public void trackUsage(String modelName, int promptTokens, int completionTokens, String userId) {
        int totalTokens = promptTokens + completionTokens;
        String correlationId = MDC.get("correlationId");

        // Update Prometheus metrics
        Counter.builder("rag.tokens.prompt")
                .description("Total prompt tokens used")
                .tag("model", modelName)
                .register(meterRegistry)
                .increment(promptTokens);

        Counter.builder("rag.tokens.completion")
                .description("Total completion tokens generated")
                .tag("model", modelName)
                .register(meterRegistry)
                .increment(completionTokens);

        log.info("TOKEN_USAGE | correlationId={} | model={} | promptTokens={} | completionTokens={} | totalTokens={} | userId={}",
                correlationId, modelName, promptTokens, completionTokens, totalTokens, userId);
    }

    /**
     * Track embedding token usage.
     */
    public void trackEmbeddingUsage(String modelName, int tokenCount, int chunkCount) {
        // Update Prometheus metrics
        Counter.builder("rag.embedding.tokens")
                .description("Total tokens used for embeddings")
                .tag("model", modelName)
                .register(meterRegistry)
                .increment(tokenCount);
                
        log.info("EMBEDDING_USAGE | model={} | tokens={} | chunks={}", modelName, tokenCount, chunkCount);
    }
}
