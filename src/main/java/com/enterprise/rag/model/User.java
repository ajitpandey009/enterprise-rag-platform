package com.enterprise.rag.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * User entity with BCrypt-hashed password and role-based access.
 * Each user belongs to exactly one tenant.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"username", "tenant_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * User roles for RBAC.
     */
    public enum Role {
        USER,       // Standard user — can upload docs, ask questions
        ADMIN,      // Tenant admin — can manage users, view audit logs
        SUPER_ADMIN // Platform admin — cross-tenant access
    }
}
