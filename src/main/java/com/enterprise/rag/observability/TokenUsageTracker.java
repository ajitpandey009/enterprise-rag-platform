package com.enterprise.rag.observability;

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

        log.info("TOKEN_USAGE | correlationId={} | model={} | promptTokens={} | completionTokens={} | totalTokens={} | userId={}",
                correlationId, modelName, promptTokens, completionTokens, totalTokens, userId);
    }

    /**
     * Track embedding token usage.
     */
    public void trackEmbeddingUsage(String modelName, int tokenCount, int chunkCount) {
        log.info("EMBEDDING_USAGE | model={} | tokens={} | chunks={}", modelName, tokenCount, chunkCount);
    }
}
