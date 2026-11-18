package com.example.videokyc.workflow.adapter.out.persistence.repository;

import com.example.videokyc.workflow.adapter.out.persistence.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowStateRepository extends JpaRepository<WorkflowStateEntity, Long> {

    List<WorkflowStateEntity> findByVersionIdOrderByDisplayOrderAsc(Long versionId);

    Optional<WorkflowStateEntity> findByVersionIdAndStateKey(Long versionId, String stateKey);
}
