package com.empresa.offboarding.dto;

import com.empresa.offboarding.enums.AccessType;
import com.empresa.offboarding.enums.CaseStatus;
import com.empresa.offboarding.enums.RiskLevel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public record OffboardingResponse(
        Long id,
        String caseNumber,
        String employeeName,
        String employeeIdentifier,
        String corporateEmail,
        String department,
        String building,
        String workArea,
        String managerName,
        String terminationType,
        OffsetDateTime effectiveAt,
        RiskLevel riskLevel,
        CaseStatus status,
        boolean confidential,
        boolean computerAssigned,
        String computerDetails,
        boolean phoneAssigned,
        String phoneDetails,
        boolean fingerprintRegistered,
        boolean faceidRegistered,
        boolean eppAssigned,
        boolean parkingAccess,
        String employeeCategory,
        String photoUrl,

        Set<AccessType> accesses,
        String otherAccesses,
        String requestedBy,
        String observations,
        OffsetDateTime createdAt,
        List<TaskResponse> tasks
) {
    public OffboardingResponse {
        accesses = accesses == null
                ? Set.of()
                : Set.copyOf(accesses);

        tasks = tasks == null
                ? List.of()
                : List.copyOf(tasks);
    }
}
