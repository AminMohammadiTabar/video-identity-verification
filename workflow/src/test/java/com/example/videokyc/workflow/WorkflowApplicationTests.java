package com.example.videokyc.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.videokyc.workflow.observability.audit.AuditEventCommand;
import com.example.videokyc.workflow.observability.audit.AuditEventRecorder;
import com.example.videokyc.workflow.observability.audit.AuditEventRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("h2")
class WorkflowApplicationTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () ->
                "jdbc:h2:mem:workflow_audit_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired AuditEventRecorder audit;
    @Autowired AuditEventRepository repository;

    @Test
    void contextLoads() {
        String type = "workflow.test.persistence_ready";
        audit.record(AuditEventCommand.of(type, "SYSTEM", "workflow", null, null,
                "WORKFLOW_MODULE", "placeholder", "SUCCESS", null, null, null));
        assertEquals(1, repository.countByEventType(type));
    }
}
