package com.example.videokyc.dms.observability.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class DmsAuditActorProvider {
    public Actor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return new Actor("ANONYMOUS", null);
        boolean service = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_AUTH_SERVICE".equals(a.getAuthority()));
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (service) return new Actor("SERVICE", safePrincipal(authentication.getName()));
        if (admin) return new Actor("ADMIN", safePrincipal(authentication.getName()));
        return new Actor("ANONYMOUS", null);
    }

    private static String safePrincipal(String value) {
        if (value == null || value.isBlank()) return null;
        // DMS uses fixed service/admin principals; never persist arbitrary principal names.
        if (value.matches("[A-Za-z0-9._-]{1,64}")) return value;
        return null;
    }

    public record Actor(String type, String id) { }
}
