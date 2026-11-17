package com.example.videokyc.workflow.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity mapping for {@code workflow.workflow_states}.
 */
@Entity
@Table(schema = "workflow", name = "workflow_states")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class WorkflowStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "state_key", nullable = false, length = 100)
    private String stateKey;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "state_type", nullable = false, length = 20)
    private String stateType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "ui_metadata_json", columnDefinition = "TEXT")
    private String uiMetadataJson;

    public WorkflowStateEntity(Long versionId,
                              String stateKey,
                              String name,
                              String description,
                              String stateType,
                              int displayOrder,
                              String uiMetadataJson) {
        this.versionId = versionId;
        this.stateKey = stateKey;
        this.name = name;
        this.description = description;
        this.stateType = stateType;
        this.displayOrder = displayOrder;
        this.uiMetadataJson = uiMetadataJson;
    }
}
