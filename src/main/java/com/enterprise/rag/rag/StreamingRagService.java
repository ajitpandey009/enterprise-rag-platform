package com.enterprise.rag.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * Streaming RAG Service — Real-time token-by-token AI responses via SSE.
 */
@Service
public class StreamingRagService {

    private static final Logger log = LoggerFactory.getLogger(StreamingRagService.class);

    private final StreamingChatLanguageModel streamingModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${app.rag.max-results}") private int maxResults;
    @Value("${app.rag.min-score}") private double minScore;
    @Value("${app.rag.system-prompt}") private String systemPrompt;

    public StreamingRagService(StreamingChatLanguageModel streamingModel,
                                EmbeddingModel embeddingModel,
                                EmbeddingStore<TextSegment> embeddingStore) {
        this.streamingModel = streamingModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public void streamAnswer(String question, SseEmitter emitter) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(question).content();
            EmbeddingSearchRequest searchReq = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding).maxResults(maxResults).minScore(minScore).build();
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchReq);
            List<EmbeddingMatch<TextSegment>> matches = result.matches();

            StringBuilder ctx = new StringBuilder();
            for (EmbeddingMatch<TextSegment> m : matches) ctx.append(m.embedded().text()).append("\n\n");

            String prompt = systemPrompt + "\n\nContext:\n" + ctx + "\n\nQuestion: " + question;

            streamingModel.chat(prompt, new StreamingChatResponseHandler() {
                @Override public void onPartialResponse(String partial) {
                    try { emitter.send(SseEmitter.event().data(partial).name("token")); }
                    catch (IOException e) { emitter.completeWithError(e); }
                }
                @Override public void onCompleteResponse(ChatResponse resp) {
                    try {
                        emitter.send(SseEmitter.event().data("[DONE]").name("done"));
                        emitter.complete();
                    } catch (IOException e) { emitter.completeWithError(e); }
                }
                @Override public void onError(Throwable error) {
                    log.error("Stream error: {}", error.getMessage());
                    emitter.completeWithError(error);
                }
            });
        } catch (Exception e) {
            log.error("Failed to stream: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        }
    }
}
