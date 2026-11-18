package com.example.videokyc.workflow.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, Long> {

    Optional<WorkflowVersionEntity> findByDefinitionIdAndVersionNumber(Long definitionId, int versionNumber);

    Optional<WorkflowVersionEntity> findFirstByDefinitionIdOrderByVersionNumberDesc(Long definitionId);

    List<WorkflowVersionEntity> findByDefinitionIdOrderByVersionNumberDesc(Long definitionId);

    List<WorkflowVersionEntity> findByDefinitionIdAndStatus(Long definitionId, String status);
}
