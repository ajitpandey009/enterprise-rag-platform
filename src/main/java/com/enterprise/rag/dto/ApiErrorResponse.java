package com.enterprise.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;

/**
 * Standard API error response format for consistent error handling.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private int status;
    private String error;
    private String message;
    private String path;
    private String correlationId;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
