package com.example.videokyc.auth.observability;

import com.example.videokyc.auth.observability.audit.AuditEventCommand;
import com.example.videokyc.auth.observability.audit.AuditEventRecorder;
import com.example.videokyc.auth.observability.audit.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("h2")
class AuditTrailH2IntegrationTest {
    @Autowired AuditEventRecorder recorder;
    @Autowired AuditEventRepository repository;
    @Autowired PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () ->
                "jdbc:h2:mem:auth_audit_tx;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Test
    void requiredAuditParticipatesInBusinessRollback() {
        String eventType = "auth.test.required_rollback";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recorder.record(AuditEventCommand.of(eventType, "SYSTEM", "test", null, null,
                    "TEST", "required", "SUCCESS", null, null, null));
            status.setRollbackOnly();
        });
        assertEquals(0, repository.countByEventType(eventType));
    }

    @Test
    void requiresNewAuditSurvivesBusinessRollback() {
        String eventType = "auth.test.independent_rollback";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recorder.recordIndependent(AuditEventCommand.of(eventType, "SYSTEM", "test", null, null,
                    "TEST", "independent", "FAILURE", "TEST_FAILURE", null, null));
            status.setRollbackOnly();
        });
        assertEquals(1, repository.countByEventType(eventType));
    }
}
