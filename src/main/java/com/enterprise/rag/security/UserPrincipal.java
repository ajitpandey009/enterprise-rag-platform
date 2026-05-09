package com.enterprise.rag.security;

import com.enterprise.rag.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Custom UserDetails implementation that carries tenant context
 * and user metadata needed throughout the request lifecycle.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final UUID tenantId;
    private final String username;
    private final String password;
    private final User.Role role;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.tenantId = user.getTenant().getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.role = user.getRole();
        this.active = user.getIsActive();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return active; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }
}
