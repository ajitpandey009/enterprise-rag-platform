package com.enterprise.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * DTOs for chat-related API operations.
 */
public class ChatDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AskRequest {
        @NotBlank(message = "Question is required")
        private String question;

        /** Optional session ID — creates new session if null */
        private String sessionId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AskResponse {
        private String answer;
        private String sessionId;
        private String messageId;
        private List<SourceChunk> sources;
        private TokenUsage tokenUsage;
        private long latencyMs;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SourceChunk {
        private String chunkId;
        private String documentName;
        private String content;
        private double similarityScore;
        private int chunkIndex;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SessionResponse {
        private String id;
        private String title;
        private int messageCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SessionDetailResponse {
        private String id;
        private String title;
        private List<MessageResponse> messages;
        private Instant createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MessageResponse {
        private String id;
        private String role;
        private String content;
        private List<SourceChunk> sources;
        private Instant createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateSessionRequest {
        private String title;
    }
}
