package com.empresa.offboarding.repository;

import com.empresa.offboarding.entity.TaskTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TaskTemplateRepository
        extends JpaRepository<TaskTemplate, Long> {

    List<TaskTemplate> findByActiveTrueOrderByIdAsc();

    List<TaskTemplate>
    findByActiveTrueAndSelectionCodeInOrderByIdAsc(
            Collection<String> selectionCodes
    );
}
