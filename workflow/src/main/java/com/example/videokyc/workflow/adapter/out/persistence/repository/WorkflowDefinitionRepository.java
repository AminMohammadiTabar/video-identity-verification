package com.example.videokyc.workflow.adapter.out.persistence.repository;

import com.example.videokyc.workflow.adapter.out.persistence.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, Long> {

    Optional<WorkflowDefinitionEntity> findByWorkflowKey(String workflowKey);

    boolean existsByWorkflowKey(String workflowKey);

    List<WorkflowDefinitionEntity> findByStatus(String status);
}
