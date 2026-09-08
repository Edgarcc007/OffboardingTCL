package com.empresa.offboarding.entity;

import com.empresa.offboarding.enums.AccessType;
import com.empresa.offboarding.enums.CaseStatus;
import com.empresa.offboarding.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "offboarding_case")
@Getter
@Setter
@NoArgsConstructor
public class OffboardingCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_number", nullable = false, unique = true, length = 30)
    private String caseNumber;

    @Column(name = "employee_name", nullable = false, length = 150)
    private String employeeName;

    @Column(name = "employee_identifier", nullable = false, length = 60)
    private String employeeIdentifier;

    @Column(name = "corporate_email", length = 150)
    private String corporateEmail;

    @Column(name = "department", length = 100)
    private String department;



    /**
     * Edificio donde se encuentra físicamente
     * el escritorio del empleado.
     */
    @Column(name = "building", length = 100)
    private String building;


    /**
     * Piso, línea, zona o área física
     * donde se encuentra el escritorio.
     */
    @Column(name = "work_area", length = 150)
    private String workArea;


    @Column(name = "manager_name", length = 150)
    private String managerName;

    @Column(name = "termination_type", nullable = false, length = 40)
    private String terminationType;

    @Column(name = "effective_at", nullable = false)
    private OffsetDateTime effectiveAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel = RiskLevel.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CaseStatus status = CaseStatus.PROGRAMADA;

    @Column(name = "confidential", nullable = false)
    private boolean confidential;

    @Column(name = "requested_by", nullable = false, length = 80)
    private String requestedBy;

    @Column(name = "computer_assigned", nullable = false)
    private boolean computerAssigned;

    @Column(name = "computer_details", length = 250)
    private String computerDetails;

    @Column(name = "phone_assigned", nullable = false)
    private boolean phoneAssigned;

    @Column(name = "phone_details", length = 250)
    private String phoneDetails;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "offboarding_case_access",
            joinColumns = @JoinColumn(name = "offboarding_case_id")
    )
    @Column(name = "access_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<AccessType> accesses = new HashSet<>();

    @Column(name = "other_accesses", columnDefinition = "text")
    private String otherAccesses;

    @Column(name = "observations", columnDefinition = "text")
    private String observations;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(
            mappedBy = "offboardingCase",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    private List<OffboardingTask> tasks = new ArrayList<>();

    /** Mantiene sincronizados ambos lados de la relacion. */
    public void addTask(OffboardingTask task) {
        if (task == null) {
            return;
        }

        if (!tasks.contains(task)) {
            tasks.add(task);
        }

        if (task.getOffboardingCase() != this) {
            task.setOffboardingCase(this);
        }
    }

    public void removeTask(OffboardingTask task) {
        if (task == null) {
            return;
        }

        tasks.remove(task);
        if (task.getOffboardingCase() == this) {
            task.setOffboardingCase(null);
        }
    }
}