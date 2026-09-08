package com.empresa.offboarding.entity;

import com.empresa.offboarding.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "offboarding_task")
@Getter
@Setter
@NoArgsConstructor
public class OffboardingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offboarding_case_id", nullable = false)
    private OffboardingCase offboardingCase;

    @Column(name = "system_name", nullable = false, length = 100)
    private String systemName;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(name = "critical", nullable = false)
    private boolean critical;

    @Column(name = "automatic", nullable = false)
    private boolean automatic;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDIENTE;

    @Column(name = "evidence_reference", length = 200)
    private String evidenceReference;

    @Column(name = "comments", columnDefinition = "text")
    private String comments;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by", length = 80)
    private String completedBy;

    @Column(name = "asset_received", nullable = false)
    private boolean assetReceived;

    @Column(name = "inventory_updated", nullable = false)
    private boolean inventoryUpdated;

    @Column(name = "inventory_reference", length = 200)
    private String inventoryReference;

    @Column(name = "validation_comments", columnDefinition = "text")
    private String validationComments;

    @Column(name = "validated_at")
    private OffsetDateTime validatedAt;

    @Column(name = "validated_by", length = 80)
    private String validatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Crea una tarea a partir de una plantilla, calculando su
     * fecha limite con el SLA definido en el catalogo.
     */
    public OffboardingTask(TaskTemplate template, OffsetDateTime effectiveAt) {
        this.systemName = template.getSystemName();
        this.taskName = template.getTaskName();
        this.critical = template.isCritical();
        this.automatic = template.isAutomatic();
        this.dueAt = effectiveAt.plusHours(template.getSlaHours());
        this.status = TaskStatus.PENDIENTE;
    }

    public OffboardingTask(
            String systemName,
            String taskName,
            boolean critical,
            boolean automatic,
            int slaHours,
            OffsetDateTime effectiveAt
    ) {
        this.systemName = systemName;
        this.taskName = taskName;
        this.critical = critical;
        this.automatic = automatic;
        this.dueAt = effectiveAt.plusHours(slaHours);
        this.status = TaskStatus.PENDIENTE;
    }

}