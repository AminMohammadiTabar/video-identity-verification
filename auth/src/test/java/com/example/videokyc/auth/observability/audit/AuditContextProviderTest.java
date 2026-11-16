package com.example.videokyc.auth.observability.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

class AuditContextProviderTest {
    private final AuditContextProvider provider = new AuditContextProvider();

    @AfterEach
    void clearMdc() { MDC.clear(); }

    @Test
    void readsRequestTraceAndClientContextFromMdc() {
        MDC.put("requestId", "req-12345");
        MDC.put("clientIp", "192.0.2.10");
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        MDC.put("spanId", "00f067aa0ba902b7");
        MDC.put("userAgent", "JUnit Test Client");

        var context = provider.current();
        assertEquals("req-12345", context.requestId());
        assertEquals("192.0.2.10", context.clientIp());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", context.traceId());
        assertEquals("00f067aa0ba902b7", context.spanId());
        assertEquals("JUnit Test Client", context.userAgent());
    }

    @Test
    void ignoresMalformedTraceIdentifiers() {
        MDC.put("traceId", "not-a-trace-id");
        MDC.put("spanId", "bad");
        var context = provider.current();
        assertNull(context.traceId());
        assertNull(context.spanId());
    }
}
