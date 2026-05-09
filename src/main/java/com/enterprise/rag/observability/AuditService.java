package com.enterprise.rag.observability;

import com.enterprise.rag.model.AuditLog;
import com.enterprise.rag.repository.AuditLogRepository;
import com.enterprise.rag.security.TenantContext;
import com.enterprise.rag.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Audit Service — Records all significant platform actions asynchronously.
 * Captures user identity, tenant, action type, resource details, and client info.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Log an audit event asynchronously to avoid blocking the main request.
     */
    @Async
    public void logAction(String action, String resourceType, UUID resourceId, Map<String, Object> details) {
        try {
            AuditLog.AuditLogBuilder builder = AuditLog.builder()
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .tenantId(TenantContext.getTenantId());

            // Extract user info from security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                builder.userId(principal.getId())
                       .username(principal.getUsername());
            }

            // Extract client info from request
            try {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest request = attrs.getRequest();
                    builder.ipAddress(getClientIp(request))
                           .userAgent(request.getHeader("User-Agent"));
                }
            } catch (Exception ignored) {
                // Request context may not be available in async context
            }

            // Serialize details to JSON
            if (details != null && !details.isEmpty()) {
                builder.details(objectMapper.writeValueAsString(details));
            }

            auditLogRepository.save(builder.build());
            log.debug("Audit log recorded: {} on {} {}", action, resourceType, resourceId);
        } catch (Exception e) {
            // Audit logging should never break the main flow
            log.error("Failed to record audit log: {}", e.getMessage(), e);
        }
    }

    /**
     * Convenience method for simple actions without details.
     */
    public void logAction(String action, String resourceType, UUID resourceId) {
        logAction(action, resourceType, resourceId, null);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
