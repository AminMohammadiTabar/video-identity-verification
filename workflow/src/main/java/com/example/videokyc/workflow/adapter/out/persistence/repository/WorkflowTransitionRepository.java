package com.example.videokyc.workflow.adapter.out.persistence.repository;

import com.example.videokyc.workflow.adapter.out.persistence.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransitionEntity, Long> {

    List<WorkflowTransitionEntity> findByVersionIdOrderByDisplayOrderAsc(Long versionId);

    List<WorkflowTransitionEntity> findByVersionIdAndFromStateIdOrderByDisplayOrderAsc(Long versionId, Long fromStateId);

    Optional<WorkflowTransitionEntity> findByVersionIdAndTransitionKey(Long versionId, String transitionKey);
}
