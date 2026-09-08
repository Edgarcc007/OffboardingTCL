package com.empresa.offboarding.dto;

import com.empresa.offboarding.enums.AccessType;
import com.empresa.offboarding.enums.RiskLevel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Set;

public record CreateOffboardingRequest(

        @NotBlank(message = "Employee name is required")
        @Size(max = 150)
        String employeeName,

        @NotBlank(message = "Employee number or identifier is required")
        @Size(max = 60)
        String employeeIdentifier,

        @Email(message = "Corporate email has an invalid format")
        @Size(max = 150)
        String corporateEmail,

        @NotBlank(message = "Department is required")
        @Size(max = 100)
        String department,

        @Size(max = 100)
        String building,

        @NotBlank(message = "Work area is required")
        @Size(max = 150)
        String workArea,

        @NotBlank(message = "Direct manager is required")
        @Size(max = 150)
        String managerName,

        @Pattern(
                regexp = "RENUNCIA|DESPIDO|FIN_DE_CONTRATO|JUBILACION|MUTUO_ACUERDO|FALLECIMIENTO|NO_ESPECIFICADO",
                message = "Unknown offboarding type"
        )
        String terminationType,

        OffsetDateTime effectiveAt,

        @NotNull(message = "Risk level is required")
        RiskLevel riskLevel,

        boolean confidential,

        boolean computerAssigned,

        @Size(max = 250)
        String computerDetails,

        boolean phoneAssigned,

        @Size(max = 250)
        String phoneDetails,

        Set<AccessType> accesses,

        @Size(max = 1000)
        String otherAccesses,

        @Size(max = 4000)
        String observations
) {}
