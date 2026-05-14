package com.enterprise.rag.rag;

import com.enterprise.rag.dto.ChatDto;
import com.enterprise.rag.exception.GlobalExceptionHandler.RagProcessingException;
import com.enterprise.rag.model.ChatMessage;
import com.enterprise.rag.model.ChatSession;
import com.enterprise.rag.model.User;
import com.enterprise.rag.observability.AuditService;
import com.enterprise.rag.observability.TokenUsageTracker;
import com.enterprise.rag.repository.ChatMessageRepository;
import com.enterprise.rag.repository.ChatSessionRepository;
import com.enterprise.rag.security.TenantContext;
import com.enterprise.rag.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Instant;
import java.util.*;

/**
 * RAG Service — Core Retrieval-Augmented Generation orchestrator.
 *
 * Pipeline:
 * 1. Embed user query using the embedding model
 * 2. Search pgvector for semantically similar document chunks
 * 3. Build an augmented prompt with retrieved context
 * 4. Send to LLM for contextual answer generation
 * 5. Persist conversation history and audit trail
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AuditService auditService;
    private final TokenUsageTracker tokenUsageTracker;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${app.rag.max-results}")
    private int maxResults;

    @Value("${app.rag.min-score}")
    private double minScore;

    @Value("${app.rag.system-prompt}")
    private String systemPrompt;

    public RagService(ChatLanguageModel chatModel,
                      EmbeddingModel embeddingModel,
                      EmbeddingStore<TextSegment> embeddingStore,
                      ChatSessionRepository sessionRepository,
                      ChatMessageRepository messageRepository,
                      AuditService auditService,
                      TokenUsageTracker tokenUsageTracker,
                      ObjectMapper objectMapper,
                      MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.auditService = auditService;
        this.tokenUsageTracker = tokenUsageTracker;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Process a RAG question: retrieve context, augment prompt, generate answer.
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    @Transactional
    public ChatDto.AskResponse askQuestion(String question, String sessionId, UserPrincipal principal) {
        long startTime = System.currentTimeMillis();
        UUID tenantId = principal.getTenantId();

        try {
            // Step 1: Get or create chat session
            ChatSession session = getOrCreateSession(sessionId, principal);

            // Step 2: Embed the user's question
            log.info("Embedding query: {}", question.substring(0, Math.min(50, question.length())));
            Embedding queryEmbedding = embeddingModel.embed(question).content();

            // Step 3: Search for similar document chunks
            log.info("Searching for similar chunks (maxResults={}, minScore={})", maxResults, minScore);
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            Timer.Sample retrievalSample = Timer.start(meterRegistry);
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            retrievalSample.stop(meterRegistry.timer("rag.retrieval.latency"));
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            log.info("Found {} relevant chunks", matches.size());

            // Step 4: Build context from retrieved chunks
            StringBuilder contextBuilder = new StringBuilder();
            List<ChatDto.SourceChunk> sources = new ArrayList<>();

            for (int i = 0; i < matches.size(); i++) {
                EmbeddingMatch<TextSegment> match = matches.get(i);
                TextSegment segment = match.embedded();

                contextBuilder.append("--- Source ").append(i + 1).append(" ---\n");
                if (segment.metadata().getString("documentName") != null) {
                    contextBuilder.append("Document: ").append(segment.metadata().getString("documentName")).append("\n");
                }
                contextBuilder.append(segment.text()).append("\n\n");

                sources.add(ChatDto.SourceChunk.builder()
                        .chunkId(match.embeddingId())
                        .documentName(segment.metadata().getString("documentName"))
                        .content(segment.text().substring(0, Math.min(200, segment.text().length())) + "...")
                        .similarityScore(match.score())
                        .chunkIndex(i)
                        .build());
            }

            // Step 5: Build augmented prompt with context
            String augmentedPrompt = String.format("""
                    %s
                    
                    Context from documents:
                    %s
                    
                    User Question: %s
                    
                    Please provide a comprehensive answer based on the context above.
                    """, systemPrompt, contextBuilder.toString(), question);

            // Step 6: Send to LLM
            log.info("Sending augmented prompt to LLM");
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));

            // Add chat history for context continuity
            List<ChatMessage> history = messageRepository.findTop20BySessionIdOrderByCreatedAtDesc(session.getId());
            Collections.reverse(history);
            for (ChatMessage hist : history) {
                if (hist.getRole() == ChatMessage.MessageRole.USER) {
                    messages.add(new UserMessage(hist.getContent()));
                } else if (hist.getRole() == ChatMessage.MessageRole.ASSISTANT) {
                    messages.add(new AiMessage(hist.getContent()));
                }
            }

            // Add current user message with context
            messages.add(new UserMessage(augmentedPrompt));

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .build();

            Timer.Sample generationSample = Timer.start(meterRegistry);
            ChatResponse response = chatModel.chat(chatRequest);
            generationSample.stop(meterRegistry.timer("rag.generation.latency"));
            
            String answer = response.aiMessage().text();

            long latencyMs = System.currentTimeMillis() - startTime;

            // Step 7: Determine token usage
            int promptTokens = 0;
            int completionTokens = 0;
            if (response.tokenUsage() != null) {
                promptTokens = response.tokenUsage().inputTokenCount() != null
                        ? response.tokenUsage().inputTokenCount() : 0;
                completionTokens = response.tokenUsage().outputTokenCount() != null
                        ? response.tokenUsage().outputTokenCount() : 0;
            }

            // Step 8: Save user message
            ChatMessage userMsg = ChatMessage.builder()
                    .session(session)
                    .tenantId(tenantId)
                    .role(ChatMessage.MessageRole.USER)
                    .content(question)
                    .build();
            messageRepository.save(userMsg);

            // Save assistant message with source references
            String sourcesJson = "[]";
            try { sourcesJson = objectMapper.writeValueAsString(sources); } catch (Exception ignored) {}

            ChatMessage assistantMsg = ChatMessage.builder()
                    .session(session)
                    .tenantId(tenantId)
                    .role(ChatMessage.MessageRole.ASSISTANT)
                    .content(answer)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .sourceChunks(sourcesJson)
                    .latencyMs(latencyMs)
                    .build();
            messageRepository.save(assistantMsg);

            // Update session
            session.setMessageCount(session.getMessageCount() + 2);
            session.setUpdatedAt(Instant.now());
            if ("New Chat".equals(session.getTitle())) {
                // Auto-generate title from first question
                session.setTitle(question.length() > 50 ? question.substring(0, 50) + "..." : question);
            }
            sessionRepository.save(session);

            // Track token usage
            tokenUsageTracker.trackUsage("chat-model", promptTokens, completionTokens,
                    principal.getId().toString());

            // Audit
            auditService.logAction("RAG_QUERY", "CHAT_SESSION", session.getId(),
                    Map.of("question", question.substring(0, Math.min(100, question.length())),
                           "chunksUsed", matches.size(),
                           "latencyMs", latencyMs));

            return ChatDto.AskResponse.builder()
                    .answer(answer)
                    .sessionId(session.getId().toString())
                    .messageId(assistantMsg.getId().toString())
                    .sources(sources)
                    .tokenUsage(ChatDto.TokenUsage.builder()
                            .promptTokens(promptTokens)
                            .completionTokens(completionTokens)
                            .totalTokens(promptTokens + completionTokens)
                            .build())
                    .latencyMs(latencyMs)
                    .build();

        } catch (Exception e) {
            log.error("RAG processing failed: {}", e.getMessage(), e);
            throw new RagProcessingException("Failed to process question: " + e.getMessage(), e);
        }
    }

    /**
     * Get existing session or create a new one.
     */
    private ChatSession getOrCreateSession(String sessionId, UserPrincipal principal) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionRepository.findByIdAndTenantId(UUID.fromString(sessionId), principal.getTenantId())
                    .orElseThrow(() -> new RagProcessingException("Session not found: " + sessionId));
        }

        // Create new session
        User userRef = new User();
        userRef.setId(principal.getId());

        ChatSession session = ChatSession.builder()
                .tenantId(principal.getTenantId())
                .user(userRef)
                .title("New Chat")
                .build();

        return sessionRepository.save(session);
    }
}
