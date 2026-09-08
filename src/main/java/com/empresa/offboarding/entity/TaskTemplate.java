package com.empresa.offboarding.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "task_template")
@Getter
@Setter
@NoArgsConstructor
public class TaskTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_name", nullable = false, length = 100)
    private String systemName;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(name = "critical", nullable = false)
    private boolean critical;

    @Column(name = "automatic", nullable = false)
    private boolean automatic;

    /** Horas desde la fecha efectiva para considerar la tarea vencida. */
    @Column(name = "sla_hours", nullable = false)
    private int slaHours = 24;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "selection_code", length = 30)
    private String selectionCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}