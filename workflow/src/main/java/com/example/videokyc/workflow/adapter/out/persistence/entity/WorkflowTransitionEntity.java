package com.example.videokyc.workflow.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity mapping for {@code workflow.workflow_transitions}.
 */
@Entity
@Table(schema = "workflow", name = "workflow_transitions")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class WorkflowTransitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "transition_key", nullable = false, length = 100)
    private String transitionKey;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "from_state_id", nullable = false)
    private Long fromStateId;

    @Column(name = "to_state_id", nullable = false)
    private Long toStateId;

    @Column(name = "guard_expression_json", columnDefinition = "TEXT")
    private String guardExpressionJson;

    @Column(name = "guard_schema_version", nullable = false)
    private short guardSchemaVersion = 1;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public WorkflowTransitionEntity(Long versionId,
                                   String transitionKey,
                                   String name,
                                   String description,
                                   Long fromStateId,
                                   Long toStateId,
                                   String guardExpressionJson,
                                   short guardSchemaVersion,
                                   int displayOrder) {
        this.versionId = versionId;
        this.transitionKey = transitionKey;
        this.name = name;
        this.description = description;
        this.fromStateId = fromStateId;
        this.toStateId = toStateId;
        this.guardExpressionJson = guardExpressionJson;
        this.guardSchemaVersion = guardSchemaVersion;
        this.displayOrder = displayOrder;
    }
}
