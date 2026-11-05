package com.example.videokyc.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/** Spring Security principal carrying a non-sensitive internal account UUID for audit tracing. */
public final class AccountPrincipal implements UserDetails, CredentialsContainer, Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private final UUID userId;
    private final UserDetails delegate;

    public AccountPrincipal(UUID userId, UserDetails delegate) {
        this.userId = Objects.requireNonNull(userId);
        this.delegate = Objects.requireNonNull(delegate);
    }

    public UUID userId() { return userId; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return delegate.getAuthorities(); }
    @Override public String getPassword() { return delegate.getPassword(); }
    @Override public String getUsername() { return delegate.getUsername(); }
    @Override public boolean isAccountNonExpired() { return delegate.isAccountNonExpired(); }
    @Override public boolean isAccountNonLocked() { return delegate.isAccountNonLocked(); }
    @Override public boolean isCredentialsNonExpired() { return delegate.isCredentialsNonExpired(); }
    @Override public boolean isEnabled() { return delegate.isEnabled(); }
    @Override public void eraseCredentials() {
        if (delegate instanceof CredentialsContainer credentialOwner) credentialOwner.eraseCredentials();
    }
}
