package com.example.videokyc.auth.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class HttpAuditFilterTest {
    @Test
    void logsEveryRequestAndIgnoresSpoofedForwardedIpAndSensitiveBody() throws Exception {
        HttpAuditFilter filter = new HttpAuditFilter("auth", "");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/health");
        request.setRemoteAddr("10.2.3.4");
        request.addHeader("X-Forwarded-For", "203.0.113.99");
        request.addHeader("X-Request-ID", "bad\nheader");
        request.setContentType("application/json");
        request.setContent("{\"password\":\"never-log-this\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(HttpAuditFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("parent", "preserved");
        try {
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals("preserved", MDC.get("parent"));
            assertNotNull(response.getHeader("X-Request-ID"));
            assertNotEquals("bad\nheader", response.getHeader("X-Request-ID"));
            assertEquals(2, appender.list.size());
            Map<String, Object> fields = appender.list.get(1).getKeyValuePairs().stream()
                    .collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
            assertEquals("http.request.completed", fields.get("event"));
            assertEquals("10.2.3.4", fields.get("clientIp"));
            assertEquals("anonymous", fields.get("actor"));
            assertEquals(200, fields.get("status"));
            assertEquals("/api/v1/health", fields.get("path"));
            assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("never-log-this")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            MDC.remove("parent");
        }
    }

    @Test
    void usesForwardedIpOnlyWhenDirectPeerIsTrusted() throws Exception {
        HttpAuditFilter filter = new HttpAuditFilter("auth", "127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ready");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.8, 127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(HttpAuditFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start(); logger.addAppender(appender);
        try {
            filter.doFilter(request, response, new MockFilterChain());
            var fields = appender.list.get(1).getKeyValuePairs().stream()
                    .collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
            assertEquals("203.0.113.8", fields.get("clientIp"));
            assertEquals("127.0.0.1", fields.get("remoteIp"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
