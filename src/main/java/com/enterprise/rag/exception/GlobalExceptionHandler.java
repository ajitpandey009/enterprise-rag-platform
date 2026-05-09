package com.enterprise.rag.exception;

import com.enterprise.rag.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * Global exception handler — Converts all exceptions into structured
 * API error responses with correlation IDs for tracing.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(DuplicateResourceException ex, WebRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(RagProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleRagError(RagProcessingException ex, WebRequest request) {
        log.error("RAG processing error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "AI processing error: " + ex.getMessage(), request);
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleDocProcessing(DocumentProcessingException ex, WebRequest request) {
        log.error("Document processing error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Document processing failed: " + ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex, WebRequest request) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds maximum allowed limit of 50MB", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAll(Exception ex, WebRequest request) {
        log.error("Unhandled exception: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String message, WebRequest request) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .build();
        return new ResponseEntity<>(error, status);
    }

    // --- Custom Exception Classes ---

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) { super(message); }
    }

    public static class DuplicateResourceException extends RuntimeException {
        public DuplicateResourceException(String message) { super(message); }
    }

    public static class RagProcessingException extends RuntimeException {
        public RagProcessingException(String message) { super(message); }
        public RagProcessingException(String message, Throwable cause) { super(message, cause); }
    }

    public static class DocumentProcessingException extends RuntimeException {
        public DocumentProcessingException(String message) { super(message); }
        public DocumentProcessingException(String message, Throwable cause) { super(message, cause); }
    }
}
