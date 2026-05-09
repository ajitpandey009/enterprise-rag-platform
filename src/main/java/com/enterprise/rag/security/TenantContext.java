package com.enterprise.rag.security;

import java.util.UUID;

/**
 * ThreadLocal-based tenant context — set from JWT claims in the
 * authentication filter and available throughout the request lifecycle.
 * Ensures tenant isolation for all database queries.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
