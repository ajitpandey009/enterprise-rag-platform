package com.enterprise.rag.service;

import com.enterprise.rag.dto.AuthDto;
import com.enterprise.rag.exception.GlobalExceptionHandler.DuplicateResourceException;
import com.enterprise.rag.model.Tenant;
import com.enterprise.rag.model.User;
import com.enterprise.rag.observability.AuditService;
import com.enterprise.rag.repository.TenantRepository;
import com.enterprise.rag.repository.UserRepository;
import com.enterprise.rag.security.JwtTokenProvider;
import com.enterprise.rag.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication Service — Handles user registration, login, and token refresh.
 * Supports multi-tenant user management with BCrypt password hashing.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.auditService = auditService;
    }

    /**
     * Register a new user. Assigns to the default tenant if no tenant specified.
     */
    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        // Check for duplicate username
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' is already taken");
        }

        // Resolve tenant
        Tenant tenant;
        if (request.getTenantName() != null && !request.getTenantName().isBlank()) {
            tenant = tenantRepository.findByName(request.getTenantName())
                    .orElseGet(() -> tenantRepository.save(
                            Tenant.builder().name(request.getTenantName()).build()));
        } else {
            tenant = tenantRepository.findById(DEFAULT_TENANT_ID)
                    .orElseThrow(() -> new RuntimeException("Default tenant not found"));
        }

        // Create user with hashed password
        User user = User.builder()
                .tenant(tenant)
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.USER)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} in tenant: {}", user.getUsername(), tenant.getName());

        // Generate tokens
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = tokenProvider.generateToken(principal);
        String refreshToken = tokenProvider.generateRefreshToken(principal);

        // Audit log
        auditService.logAction("REGISTER", "USER", user.getId(),
                Map.of("username", user.getUsername(), "tenant", tenant.getName()));

        return buildAuthResponse(accessToken, refreshToken, user, tenant);
    }

    /**
     * Authenticate user and return JWT tokens.
     */
    @Transactional
    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditService.logAction("LOGIN_FAILED", "USER", user.getId(),
                    Map.of("username", request.getUsername()));
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!user.getIsActive()) {
            throw new BadCredentialsException("Account is disabled");
        }

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Generate tokens
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = tokenProvider.generateToken(principal);
        String refreshToken = tokenProvider.generateRefreshToken(principal);

        auditService.logAction("LOGIN", "USER", user.getId());
        log.info("User logged in: {}", user.getUsername());

        return buildAuthResponse(accessToken, refreshToken, user, user.getTenant());
    }

    private AuthDto.AuthResponse buildAuthResponse(String accessToken, String refreshToken,
                                                    User user, Tenant tenant) {
        return AuthDto.AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400)
                .user(AuthDto.UserInfo.builder()
                        .id(user.getId().toString())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole().name())
                        .tenantId(tenant.getId().toString())
                        .tenantName(tenant.getName())
                        .build())
                .build();
    }
}
