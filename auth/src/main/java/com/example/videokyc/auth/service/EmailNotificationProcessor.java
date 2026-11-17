package com.example.videokyc.auth.service;

import com.example.videokyc.auth.domain.EmailNotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled processor for sending queued email notifications.
 * <p>
 * Operates only when explicitly enabled via {@code app.notifications.email.enabled=true}.
 * Periodically polls the transactional outbox, claims due batches with lease locks,
 * dispatches emails via SMTP using {@link JavaMailSender}, and updates outbox state with exponential backoff.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.email.enabled", havingValue = "true")
public class EmailNotificationProcessor {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationProcessor.class);
    private final EmailOutboxService outbox;
    private final JavaMailSender mailSender;
    private final ObservationRegistry observations;
    private final String from;

    public EmailNotificationProcessor(EmailOutboxService outbox, JavaMailSender mailSender,
                                      ObservationRegistry observations,
                                      @Value("${app.notifications.email.from}") String from) {
        this.outbox = outbox;
        this.mailSender = mailSender;
        this.observations = observations;
        if (from == null || from.isBlank() || from.toLowerCase(java.util.Locale.ROOT).endsWith(".invalid")) {
            throw new IllegalStateException("EMAIL_FROM must be configured when email notifications are enabled");
        }
        this.from = from;
    }

    @Scheduled(fixedDelayString = "${app.notifications.email.poll-interval-ms:15000}")
    public void processDueNotifications() {
        for (var id : outbox.claimDueBatch()) send(id);
    }

    private void send(java.util.UUID id) {
        var item = outbox.message(id);
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(item.recipient());
        message.setSubject(item.type() == EmailNotificationType.VERIFICATION_APPROVED
                ? "نتیجه احراز هویت ویدئویی" : "نتیجه بررسی احراز هویت ویدئویی");
        message.setText(item.type() == EmailNotificationType.VERIFICATION_APPROVED
                ? "کاربر گرامی،\n\nاحراز هویت ویدئویی شما با موفقیت تأیید شد.\n\nبا احترام، سامانه احراز هویت"
                : "کاربر گرامی،\n\nمتأسفانه احراز هویت ویدئویی شما تأیید نشد. لطفاً وارد سامانه شوید و پس از مطالعه نتیجه بررسی، ویدئوی جدیدی ارسال کنید.\n\nبا احترام، سامانه احراز هویت");
        try {
            Observation.createNotStarted("auth.notification.email.send", observations)
                    .lowCardinalityKeyValue("notification.type", item.type().name())
                    .observe(() -> {
                        try {
                            mailSender.send(message);
                            outbox.markSent(id);
                            return Boolean.TRUE;
                        } catch (MailException failure) {
                            outbox.markFailed(id, failure.getClass().getSimpleName());
                            throw failure;
                        }
                    });
        } catch (MailException failure) {
            String safeCode = failure.getClass().getSimpleName();
            log.atWarn().addKeyValue("event", "email.delivery.failed")
                    .addKeyValue("outboxId", id.toString()).addKeyValue("failureType", safeCode)
                    .log("Verification notification email delivery failed; retry scheduled");
            return;
        }
        log.atInfo().addKeyValue("event", "email.delivery.succeeded")
                .addKeyValue("outboxId", id.toString()).log("Verification notification email delivered");
    }
}
