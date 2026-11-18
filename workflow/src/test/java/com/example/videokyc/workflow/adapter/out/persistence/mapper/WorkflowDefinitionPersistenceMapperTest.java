package com.example.videokyc.workflow.adapter.out.persistence.mapper;

import com.example.videokyc.workflow.adapter.out.persistence.entity.WorkflowDefinitionEntity;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinitionStatus;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDefinitionPersistenceMapperTest {

    private final WorkflowDefinitionPersistenceMapper mapper = new WorkflowDefinitionPersistenceMapper();

    @Test
    @DisplayName("Should map WorkflowDefinition domain aggregate to entity and back without data loss")
    void shouldMapDomainToEntityAndBack() {
        Instant now = Instant.parse("2026-11-18T10:00:00Z");
        WorkflowDefinition domain = new WorkflowDefinition(
                101L,
                WorkflowKey.of("KYC_FLOW"),
                "KYC Workflow",
                "Customer onboarding workflow",
                WorkflowDefinitionStatus.ACTIVE,
                1001L,
                1002L,
                now,
                "admin",
                now.plusSeconds(60),
                "operator",
                3L
        );

        WorkflowDefinitionEntity entity = mapper.toEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(101L);
        assertThat(entity.getWorkflowKey()).isEqualTo("KYC_FLOW");
        assertThat(entity.getName()).isEqualTo("KYC Workflow");
        assertThat(entity.getDescription()).isEqualTo("Customer onboarding workflow");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getActiveVersionId()).isEqualTo(1001L);
        assertThat(entity.getPendingVersionId()).isEqualTo(1002L);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getCreatedBy()).isEqualTo("admin");
        assertThat(entity.getUpdatedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(entity.getUpdatedBy()).isEqualTo("operator");
        assertThat(entity.getRowVersion()).isEqualTo(3L);

        WorkflowDefinition roundtripped = mapper.toDomain(entity);

        assertThat(roundtripped).isNotNull();
        assertThat(roundtripped.getId()).isEqualTo(domain.getId());
        assertThat(roundtripped.getWorkflowKey()).isEqualTo(domain.getWorkflowKey());
        assertThat(roundtripped.getName()).isEqualTo(domain.getName());
        assertThat(roundtripped.getDescription()).isEqualTo(domain.getDescription());
        assertThat(roundtripped.getStatus()).isEqualTo(domain.getStatus());
        assertThat(roundtripped.getActiveVersionId()).isEqualTo(domain.getActiveVersionId());
        assertThat(roundtripped.getPendingVersionId()).isEqualTo(domain.getPendingVersionId());
        assertThat(roundtripped.getCreatedAt()).isEqualTo(domain.getCreatedAt());
        assertThat(roundtripped.getCreatedBy()).isEqualTo(domain.getCreatedBy());
        assertThat(roundtripped.getUpdatedAt()).isEqualTo(domain.getUpdatedAt());
        assertThat(roundtripped.getUpdatedBy()).isEqualTo(domain.getUpdatedBy());
        assertThat(roundtripped.getRowVersion()).isEqualTo(domain.getRowVersion());
    }

    @Test
    @DisplayName("Should handle null entity and domain gracefully")
    void shouldHandleNull() {
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }
}
