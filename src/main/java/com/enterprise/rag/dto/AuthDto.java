package com.enterprise.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTOs for authentication endpoints.
 */
public class AuthDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RegisterRequest {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 100)
        private String username;

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100)
        private String password;

        private String email;
        private String fullName;

        /** Optional — defaults to the default tenant if not provided */
        private String tenantName;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private long expiresIn;
        private UserInfo user;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UserInfo {
        private String id;
        private String username;
        private String email;
        private String fullName;
        private String role;
        private String tenantId;
        private String tenantName;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RefreshRequest {
        @NotBlank private String refreshToken;
    }
}
