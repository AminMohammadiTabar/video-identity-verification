package com.example.videokyc.workflow.adapter.out.persistence.mapper;

import com.example.videokyc.workflow.adapter.out.persistence.entity.WorkflowDefinitionEntity;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinitionStatus;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import org.springframework.stereotype.Component;

/**
 * Mapper between {@link WorkflowDefinition} domain aggregate and {@link WorkflowDefinitionEntity} JPA entity.
 */
@Component
public class WorkflowDefinitionPersistenceMapper {

    public WorkflowDefinition toDomain(WorkflowDefinitionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new WorkflowDefinition(
                entity.getId(),
                WorkflowKey.of(entity.getWorkflowKey()),
                entity.getName(),
                entity.getDescription(),
                WorkflowDefinitionStatus.valueOf(entity.getStatus()),
                entity.getActiveVersionId(),
                entity.getPendingVersionId(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy(),
                entity.getRowVersion()
        );
    }

    public WorkflowDefinitionEntity toEntity(WorkflowDefinition domain) {
        if (domain == null) {
            return null;
        }
        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity(
                domain.getWorkflowKey().value(),
                domain.getName(),
                domain.getDescription(),
                domain.getStatus().name(),
                domain.getActiveVersionId(),
                domain.getPendingVersionId(),
                domain.getCreatedAt(),
                domain.getCreatedBy(),
                domain.getUpdatedAt(),
                domain.getUpdatedBy()
        );
        entity.setId(domain.getId());
        entity.setRowVersion(domain.getRowVersion());
        return entity;
    }
}
