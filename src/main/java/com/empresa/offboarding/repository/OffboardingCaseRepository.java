package com.empresa.offboarding.repository;

import com.empresa.offboarding.entity.OffboardingCase;
import com.empresa.offboarding.enums.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OffboardingCaseRepository extends JpaRepository<OffboardingCase, Long> {

    List<OffboardingCase> findAllByOrderByCreatedAtDesc();

    Optional<OffboardingCase> findByCaseNumber(String caseNumber);

    List<OffboardingCase> findByStatusOrderByEffectiveAtAsc(CaseStatus status);

    List<OffboardingCase> findByEmployeeIdentifierOrderByCreatedAtDesc(String employeeIdentifier);

    List<OffboardingCase> findByEmployeeIdentifierAndStatusOrderByCreatedAtDesc(String employeeIdentifier, CaseStatus status);
}