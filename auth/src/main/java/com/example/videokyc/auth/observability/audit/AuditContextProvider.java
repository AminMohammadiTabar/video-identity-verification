package com.example.videokyc.auth.observability.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditContextProvider {
    public AuditContext current() {
        String requestId = safe(MDC.get("requestId"), 64);
        String clientIp = safe(MDC.get("clientIp"), 45);
        String traceId = hex(MDC.get("traceId"), 32);
        String spanId = hex(MDC.get("spanId"), 16);
        String userAgent = safe(MDC.get("userAgent"), 512);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            userAgent = safe(request.getHeader("User-Agent"), 512);
        }
        return new AuditContext(requestId, traceId, spanId, clientIp, userAgent);
    }

    private static String safe(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "_");
        return cleaned.substring(0, Math.min(cleaned.length(), max));
    }

    private static String hex(String value, int length) {
        return value != null && value.matches("(?i)[0-9a-f]{" + length + "}") ? value : null;
    }

    public record AuditContext(String requestId, String traceId, String spanId,
                               String clientIp, String userAgent) { }
}
