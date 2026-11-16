package com.example.videokyc.auth.domain;

import com.example.videokyc.auth.domain.EmailNotificationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EmailOutboxEntityTest {
    @Test
    void retriesWithBackoffAndClearsRecipientAfterDeadLetter() {
        Instant now = Instant.parse("2026-11-15T12:00:00Z");
        var item = new EmailOutboxEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "person@example.com", EmailNotificationType.VERIFICATION_REJECTED, now);

        item.claim(now.plusSeconds(300));
        item.retry(now.plusSeconds(120), "MailSendException", 2);
        assertEquals(EmailOutboxStatus.PENDING, item.getStatus());
        assertEquals("person@example.com", item.getRecipientEmail());
        assertEquals(1, item.getAttemptCount());

        item.claim(now.plusSeconds(420));
        item.retry(now.plusSeconds(600), "MailSendException", 2);
        assertEquals(EmailOutboxStatus.DEAD, item.getStatus());
        assertNull(item.getRecipientEmail());
    }

    @Test
    void successfulDeliveryClearsRecipient() {
        Instant now = Instant.now();
        var item = new EmailOutboxEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "person@example.com", EmailNotificationType.VERIFICATION_APPROVED, now);
        item.claim(now.plusSeconds(300));
        item.sent(now.plusSeconds(1));
        assertEquals(EmailOutboxStatus.SENT, item.getStatus());
        assertNull(item.getRecipientEmail());
        assertNotNull(item.getSentAt());
    }
}
