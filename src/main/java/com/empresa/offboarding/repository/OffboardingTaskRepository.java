package com.empresa.offboarding.repository;

import com.empresa.offboarding.entity.OffboardingTask;
import com.empresa.offboarding.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface OffboardingTaskRepository extends JpaRepository<OffboardingTask, Long> {

    List<OffboardingTask> findByOffboardingCaseIdOrderByIdAsc(Long offboardingCaseId);

    /** Base para el modulo de recordatorios y escalamiento. */
    List<OffboardingTask> findByStatusInAndDueAtBeforeOrderByDueAtAsc(
            Collection<TaskStatus> statuses, OffsetDateTime reference);

    long countByOffboardingCaseIdAndStatus(Long offboardingCaseId, TaskStatus status);
}