package com.example.videokyc.auth.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import com.example.videokyc.auth.security.AccountPrincipal;

/** Operational audit log, NOT a substitute for a durable compliance audit/outbox. */
public final class AuthActionAudit {
    private static final Logger log = LoggerFactory.getLogger(AuthActionAudit.class);
    private AuthActionAudit() { }

    public static UUID userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    public static void record(String action, String outcome, UUID userId) {
        log.atInfo().addKeyValue("event", "auth.action")
                .addKeyValue("action", action).addKeyValue("outcome", outcome)
                .addKeyValue("userId", userId == null ? "-" : userId.toString())
                .log("AUTH action");
    }
}
