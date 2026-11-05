package com.example.videokyc.workflow.observability;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Access log for every incoming request, including authentication failures and Swagger/static resources.
 * Intentionally NEVER reads headers such as Authorization/Cookie, query strings, form fields or bodies.
 * X-Forwarded-For is considered only for explicitly configured trusted reverse-proxy socket IPs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class HttpAuditFilter extends OncePerRequestFilter {
    public static final String ACTOR_ATTRIBUTE = HttpAuditFilter.class.getName() + ".actor";
    private static final Logger log = LoggerFactory.getLogger(HttpAuditFilter.class);
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern IP_CHARS = Pattern.compile("[0-9a-fA-F:.]{3,45}");
    private final String service;
    private final Set<String> trustedProxies;

    public HttpAuditFilter(@Value("${spring.application.name:application}") String service,
                           @Value("${app.audit.trusted-proxies:}") String proxies) {
        this.service = service;
        this.trustedProxies = Arrays.stream(proxies.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String candidate = request.getHeader("X-Request-ID");
        String id = candidate != null && REQUEST_ID.matcher(candidate).matches()
                ? candidate : UUID.randomUUID().toString();
        String remoteIp = safeText(request.getRemoteAddr(), 45);
        String clientIp = clientIp(request, remoteIp);
        String path = safeText(request.getRequestURI(), 250); // never log the query string
        String method = safeText(request.getMethod(), 16);
        String userAgent = safeText(request.getHeader("User-Agent"), 160);
        long started = System.nanoTime();
        AtomicBoolean completed = new AtomicBoolean(false);
        Map<String, String> originalMdc = MDC.getCopyOfContextMap();
        MDC.put("requestId", id);
        MDC.put("clientIp", clientIp);
        MDC.put("service", service);
        response.setHeader("X-Request-ID", id);
        log.atInfo().addKeyValue("event", "http.request.started")
                .addKeyValue("method", method).addKeyValue("path", path)
                .addKeyValue("remoteIp", remoteIp).addKeyValue("userAgent", userAgent)
                .addKeyValue("requestBytes", request.getContentLengthLong())
                .log("HTTP request started");
        try {
            chain.doFilter(request, response);
            if (request.isAsyncStarted()) {
                request.getAsyncContext().addListener(new AsyncListener() {
                    @Override public void onComplete(AsyncEvent event) {
                        finish(request, response, id, method, path, remoteIp, clientIp, started,
                                completed, response.getStatus(), "completed");
                    }
                    @Override public void onTimeout(AsyncEvent event) {
                        finish(request, response, id, method, path, remoteIp, clientIp, started,
                                completed, 503, "timeout");
                    }
                    @Override public void onError(AsyncEvent event) {
                        finish(request, response, id, method, path, remoteIp, clientIp, started,
                                completed, 500, "error");
                    }
                    @Override public void onStartAsync(AsyncEvent event) {
                        event.getAsyncContext().addListener(this);
                    }
                });
            } else {
                finish(request, response, id, method, path, remoteIp, clientIp, started,
                        completed, response.getStatus(), "completed");
            }
        } catch (IOException | ServletException | RuntimeException ex) {
            finish(request, response, id, method, path, remoteIp, clientIp, started,
                    completed, Math.max(500, response.getStatus()), "error");
            throw ex;
        } finally {
            if (originalMdc == null) MDC.clear();
            else MDC.setContextMap(originalMdc);
        }
    }

    private void finish(HttpServletRequest request, HttpServletResponse response, String id,
                        String method, String path, String remoteIp, String clientIp,
                        long started, AtomicBoolean completed, int status, String result) {
        if (!completed.compareAndSet(false, true)) return;
        long millis = (System.nanoTime() - started) / 1_000_000;
        Object actor = request.getAttribute(ACTOR_ATTRIBUTE);
        log.atInfo().addKeyValue("event", "http.request.completed")
                .addKeyValue("requestId", id).addKeyValue("service", service)
                .addKeyValue("method", method).addKeyValue("path", path)
                .addKeyValue("remoteIp", remoteIp).addKeyValue("clientIp", clientIp)
                .addKeyValue("actor", actor == null ? "anonymous" : actor.toString())
                .addKeyValue("status", status).addKeyValue("durationMs", millis)
                .addKeyValue("outcome", result).log("HTTP request completed");
    }

    private String clientIp(HttpServletRequest request, String remoteIp) {
        if (!trustedProxies.contains(remoteIp)) return remoteIp;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null) return remoteIp;
        String first = forwarded.split(",", 2)[0].trim();
        if (!IP_CHARS.matcher(first).matches()) return remoteIp;
        try {
            InetAddress parsed = InetAddress.getByName(first);
            return parsed.getHostAddress();
        } catch (UnknownHostException | SecurityException ex) {
            return remoteIp;
        }
    }

    private static String safeText(String input, int limit) {
        if (input == null) return "-";
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < Math.min(input.length(), limit); i++) {
            char c = input.charAt(i);
            cleaned.append(Character.isISOControl(c) ? '_' : c);
        }
        return cleaned.toString();
    }
}
