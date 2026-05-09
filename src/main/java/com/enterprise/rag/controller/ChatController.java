package com.enterprise.rag.controller;

import com.enterprise.rag.dto.ChatDto;
import com.enterprise.rag.exception.GlobalExceptionHandler.ResourceNotFoundException;
import com.enterprise.rag.model.ChatMessage;
import com.enterprise.rag.model.ChatSession;
import com.enterprise.rag.model.User;
import com.enterprise.rag.rag.RagService;
import com.enterprise.rag.rag.StreamingRagService;
import com.enterprise.rag.repository.ChatMessageRepository;
import com.enterprise.rag.repository.ChatSessionRepository;
import com.enterprise.rag.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "AI-powered RAG chat")
public class ChatController {

    private final RagService ragService;
    private final StreamingRagService streamingRagService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    public ChatController(RagService ragService,
                           StreamingRagService streamingRagService,
                           ChatSessionRepository sessionRepository,
                           ChatMessageRepository messageRepository) {
        this.ragService = ragService;
        this.streamingRagService = streamingRagService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question — returns full RAG-augmented answer")
    public ResponseEntity<ChatDto.AskResponse> askQuestion(
            @Valid @RequestBody ChatDto.AskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ChatDto.AskResponse response = ragService.askQuestion(
                request.getQuestion(), request.getSessionId(), principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream AI response via Server-Sent Events")
    public SseEmitter streamChat(@RequestParam String question,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 min timeout
        streamingRagService.streamAnswer(question, emitter);
        return emitter;
    }

    @PostMapping("/sessions")
    @Operation(summary = "Create a new chat session")
    public ResponseEntity<ChatDto.SessionResponse> createSession(
            @RequestBody(required = false) ChatDto.CreateSessionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        User userRef = new User();
        userRef.setId(principal.getId());

        ChatSession session = ChatSession.builder()
                .tenantId(principal.getTenantId())
                .user(userRef)
                .title(request != null && request.getTitle() != null ? request.getTitle() : "New Chat")
                .build();
        session = sessionRepository.save(session);

        return ResponseEntity.ok(toSessionResponse(session));
    }

    @GetMapping("/sessions")
    @Operation(summary = "List all chat sessions for the current user")
    public ResponseEntity<List<ChatDto.SessionResponse>> listSessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ChatSession> sessions = sessionRepository
                .findByUserIdAndTenantIdOrderByUpdatedAtDesc(principal.getId(), principal.getTenantId());
        return ResponseEntity.ok(sessions.stream().map(this::toSessionResponse).toList());
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Get a chat session with all messages")
    public ResponseEntity<ChatDto.SessionDetailResponse> getSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ChatSession session = sessionRepository.findByIdAndTenantId(id, principal.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));

        List<ChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(id);

        List<ChatDto.MessageResponse> msgResponses = messages.stream()
                .map(m -> ChatDto.MessageResponse.builder()
                        .id(m.getId().toString())
                        .role(m.getRole().name())
                        .content(m.getContent())
                        .sources(Collections.emptyList())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(ChatDto.SessionDetailResponse.builder()
                .id(session.getId().toString())
                .title(session.getTitle())
                .messages(msgResponses)
                .createdAt(session.getCreatedAt())
                .build());
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "Delete a chat session")
    public ResponseEntity<Void> deleteSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ChatSession session = sessionRepository.findByIdAndTenantId(id, principal.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
        sessionRepository.delete(session);
        return ResponseEntity.noContent().build();
    }

    private ChatDto.SessionResponse toSessionResponse(ChatSession s) {
        return ChatDto.SessionResponse.builder()
                .id(s.getId().toString())
                .title(s.getTitle())
                .messageCount(s.getMessageCount())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
