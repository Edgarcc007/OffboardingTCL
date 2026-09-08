package com.empresa.offboarding.service;

import com.empresa.offboarding.dto.CompleteTaskRequest;
import com.empresa.offboarding.dto.CreateOffboardingRequest;
import com.empresa.offboarding.dto.OffboardingResponse;
import com.empresa.offboarding.dto.ReopenTaskRequest;
import com.empresa.offboarding.dto.TaskResponse;
import com.empresa.offboarding.dto.ValidateAssetTaskRequest;
import com.empresa.offboarding.entity.OffboardingCase;
import com.empresa.offboarding.entity.OffboardingTask;
import com.empresa.offboarding.entity.TaskTemplate;
import com.empresa.offboarding.enums.AccessType;
import com.empresa.offboarding.enums.CaseStatus;
import com.empresa.offboarding.enums.TaskStatus;
import com.empresa.offboarding.repository.OffboardingCaseRepository;
import com.empresa.offboarding.repository.OffboardingTaskRepository;
import com.empresa.offboarding.repository.TaskTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OffboardingService {

    private final OffboardingCaseRepository caseRepository;
    private final OffboardingTaskRepository taskRepository;
    private final TaskTemplateRepository templateRepository;
    private final CaseNumberGenerator caseNumberGenerator;
    private final AuditService auditService;

    @Transactional
    public OffboardingResponse create(
            CreateOffboardingRequest request,
            String requestedBy
    ) {
        Set<AccessType> accesses =
                request.accesses() == null
                        || request.accesses().isEmpty()
                        ? EnumSet.noneOf(AccessType.class)
                        : EnumSet.copyOf(request.accesses());

Set<String> selectionCodes = new HashSet<>();

        if (request.computerAssigned()) {
            selectionCodes.add("COMPUTER");
        }

        if (request.phoneAssigned()) {
            selectionCodes.add("PHONE");
        }

        accesses.stream()
                .filter(access ->
                        access != AccessType.OTHER)
                .map(Enum::name)
                .forEach(selectionCodes::add);

        List<TaskTemplate> templates =
                selectionCodes.isEmpty()
                        ? List.of()
                        : templateRepository
                            .findByActiveTrueAndSelectionCodeInOrderByIdAsc(
                                    selectionCodes
                            );

        Set<String> foundCodes = new HashSet<>();

        templates.forEach(template ->
                foundCodes.add(template.getSelectionCode()));

        Set<String> missingCodes =
                new HashSet<>(selectionCodes);

        missingCodes.removeAll(foundCodes);

        if (!missingCodes.isEmpty()) {
            throw new IllegalStateException(
                    "No active task templates exist for: "
                            + missingCodes
            );
        }

        String terminationType =
                cleanNullable(
                        request.terminationType()
                );

        if (terminationType == null) {
            terminationType =
                    "NO_ESPECIFICADO";
        }

        OffsetDateTime effectiveAt =
                request.effectiveAt() == null
                        ? OffsetDateTime.now()
                        : request.effectiveAt();


        OffboardingCase offboardingCase =
                new OffboardingCase();

        offboardingCase.setCaseNumber(
                caseNumberGenerator.next());
        offboardingCase.setEmployeeName(
                request.employeeName());
        offboardingCase.setEmployeeIdentifier(
                request.employeeIdentifier());
        offboardingCase.setCorporateEmail(
                cleanNullable(
                        request.corporateEmail()
                )
        );
        offboardingCase.setDepartment(
                request.department().trim());
        offboardingCase.setBuilding(
                cleanNullable(
                        request.building()
                )
        );
        offboardingCase.setWorkArea(
                request.workArea().trim());
        offboardingCase.setManagerName(
                cleanNullable(request.managerName()));
        offboardingCase.setTerminationType(
                terminationType);
        offboardingCase.setEffectiveAt(
                effectiveAt);
        offboardingCase.setRiskLevel(
                request.riskLevel());
        offboardingCase.setConfidential(
                request.confidential());

        offboardingCase.setComputerAssigned(
                request.computerAssigned());

        offboardingCase.setComputerDetails(
                request.computerAssigned()
                        ? cleanNullable(
                            request.computerDetails())
                        : null
        );

        offboardingCase.setPhoneAssigned(
                request.phoneAssigned());

        offboardingCase.setPhoneDetails(
                request.phoneAssigned()
                        ? cleanNullable(
                            request.phoneDetails())
                        : null
        );


        offboardingCase.setFingerprintRegistered(
                request.fingerprintRegistered());

        offboardingCase.setFaceidRegistered(
                request.faceidRegistered());

        offboardingCase.setEppAssigned(
                request.eppAssigned());

        offboardingCase.setParkingAccess(
                request.parkingAccess());

        offboardingCase.setEmployeeCategory(
                cleanNullable(request.employeeCategory()));
        offboardingCase.setAccesses(
                new HashSet<>(accesses));

        offboardingCase.setOtherAccesses(
                accesses.contains(AccessType.OTHER)
                        ? cleanNullable(
                            request.otherAccesses())
                        : null
        );

        offboardingCase.setObservations(
                cleanNullable(
                        request.observations()
                )
        );
        offboardingCase.setRequestedBy(
                requestedBy);
        offboardingCase.setStatus(
                CaseStatus.PROGRAMADA);

        for (TaskTemplate template : templates) {
            OffboardingTask task =
                    new OffboardingTask(
                            template,
                            effectiveAt
                    );

            if ("COMPUTER".equals(
                    template.getSelectionCode())
                    && !isBlank(
                        request.computerDetails())) {
                task.setTaskName(
                        withReference(
                                template.getTaskName(),
                                request.computerDetails()
                        )
                );
            }

            if ("PHONE".equals(
                    template.getSelectionCode())
                    && !isBlank(
                        request.phoneDetails())) {
                task.setTaskName(
                        withReference(
                                template.getTaskName(),
                                request.phoneDetails()
                        )
                );
            }

            offboardingCase.addTask(task);
        }

        if (accesses.contains(AccessType.OTHER)) {
            offboardingCase.addTask(
                    new OffboardingTask(
                            "Otros accesos",
                            withReference(
                                    "Revocar accesos adicionales especificados por RH",
                                    request.otherAccesses()
                            ),
                            false,
                            false,
                            4,
                            effectiveAt
                    )
            );
        }

        OffboardingCase saved =
                caseRepository.save(offboardingCase);

        auditService.record(
                requestedBy,
                "CASE_CREATED",
                "OffboardingCase",
                saved.getId(),
                "caso=" + saved.getCaseNumber()
                        + "; empleado="
                        + saved.getEmployeeName()
                        + "; riesgo="
                        + saved.getRiskLevel()
                        + "; computadora="
                        + saved.isComputerAssigned()
                        + "; telefono="
                        + saved.isPhoneAssigned()
                        + "; accesos="
                        + saved.getAccesses()
                        + "; otrosAccesos="
                        + valueOrDash(
                            saved.getOtherAccesses())
                        + "; tareas="
                        + saved.getTasks().size()
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OffboardingResponse> findAll() {
        return caseRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OffboardingResponse findById(Long id) {
        return toResponse(requireCase(id));
    }

    @Transactional
    public TaskResponse completeTask(
            Long taskId,
            CompleteTaskRequest request,
            String completedBy
    ) {
        OffboardingTask task = requireTask(taskId);

        if (task.getStatus() == TaskStatus.COMPLETADA
                || task.getStatus() == TaskStatus.VALIDADA
                || task.getStatus() == TaskStatus.NO_APLICA) {
            throw new IllegalStateException(
                    "The task has already been processed. "
                            + "An administrator must reopen it before it can be modified"
            );
        }

        task.setStatus(TaskStatus.COMPLETADA);
        task.setEvidenceReference(
                request.evidenceReference());
        task.setComments(request.comments());
        task.setCompletedAt(OffsetDateTime.now());
        task.setCompletedBy(completedBy);

        taskRepository.save(task);

        auditService.record(
                completedBy,
                "TASK_COMPLETED",
                "OffboardingTask",
                task.getId(),
                "sistema=" + task.getSystemName()
                        + "; critica="
                        + task.isCritical()
                        + "; referencia="
                        + request.evidenceReference()
        );

        updateCaseStatus(
                task.getOffboardingCase());

        return toTaskResponse(task);
    }

    @Transactional
    public TaskResponse validateTask(
            Long taskId,
            ValidateAssetTaskRequest request,
            String validatedBy
    ) {
        OffboardingTask task = requireTask(taskId);

        if (!isAssetTask(task)) {
            throw new IllegalStateException(
                    "Administrative validation only applies "
                            + "to computers and phones"
            );
        }

        if (task.getStatus()
                != TaskStatus.COMPLETADA) {
            throw new IllegalStateException(
                    "Solo se puede validar un activo "
                            + "en estado COMPLETADA"
            );
        }

        String completedBy = task.getCompletedBy();

        if (validatedBy != null
                && completedBy != null
                && validatedBy.equalsIgnoreCase(
                    completedBy)) {
            throw new IllegalStateException(
                    "La validación debe hacerla una persona "
                            + "distinta a quien recibió o procesó el activo"
            );
        }

        if (!request.assetReceived()) {
            throw new IllegalArgumentException(
                    "Debes confirmar la recepción física del activo"
            );
        }

        if (!request.inventoryUpdated()) {
            throw new IllegalArgumentException(
                    "Debes confirmar la actualización del inventario"
            );
        }

        String inventoryReference =
                request.inventoryReference().trim();

        String validationComments =
                cleanNullable(
                    request.validationComments());

        task.setAssetReceived(true);
        task.setInventoryUpdated(true);
        task.setInventoryReference(
                inventoryReference);
        task.setValidationComments(
                validationComments);
        task.setStatus(TaskStatus.VALIDADA);
        task.setValidatedAt(
                OffsetDateTime.now());
        task.setValidatedBy(validatedBy);

        taskRepository.save(task);

        auditService.record(
                validatedBy,
                "ASSET_RECEIPT_VALIDATED",
                "OffboardingTask",
                task.getId(),
                "sistema=" + task.getSystemName()
                        + "; activoRecibido=true"
                        + "; inventarioActualizado=true"
                        + "; referenciaInventario="
                        + valueOrDash(
                            inventoryReference)
                        + "; comentariosValidacion="
                        + valueOrDash(
                            validationComments)
                        + "; ejecutadaPor="
                        + valueOrDash(
                            task.getCompletedBy())
        );

        updateCaseStatus(
                task.getOffboardingCase());

        return toTaskResponse(task);
    }


    @Transactional
    public TaskResponse reopenTask(
            Long taskId,
            ReopenTaskRequest request,
            String reopenedBy
    ) {
        OffboardingTask task = requireTask(taskId);

        if (task.getStatus()
                != TaskStatus.COMPLETADA
                && task.getStatus()
                != TaskStatus.VALIDADA
                && task.getStatus()
                != TaskStatus.NO_APLICA) {
            throw new IllegalStateException(
                    "Solo puede reabrirse una tarea que ya fue procesada"
            );
        }

        TaskStatus previousStatus =
                task.getStatus();
        String previousEvidence =
                task.getEvidenceReference();
        String previousCompletedBy =
                task.getCompletedBy();
        String previousValidatedBy =
                task.getValidatedBy();
        String previousInventoryReference =
                task.getInventoryReference();
        boolean previousAssetReceived =
                task.isAssetReceived();
        boolean previousInventoryUpdated =
                task.isInventoryUpdated();

        String reason = request.reason()
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();

        task.setStatus(TaskStatus.PENDIENTE);
        task.setEvidenceReference(null);
        task.setComments(null);
        task.setCompletedAt(null);
        task.setCompletedBy(null);
        task.setValidatedAt(null);
        task.setValidatedBy(null);
        task.setAssetReceived(false);
        task.setInventoryUpdated(false);
        task.setInventoryReference(null);
        task.setValidationComments(null);

        taskRepository.save(task);

        auditService.record(
                reopenedBy,
                "TASK_REOPENED",
                "OffboardingTask",
                task.getId(),
                "estadoAnterior="
                        + previousStatus
                        + "; sistema="
                        + task.getSystemName()
                        + "; evidenciaAnterior="
                        + valueOrDash(
                            previousEvidence)
                        + "; ejecutadaPor="
                        + valueOrDash(
                            previousCompletedBy)
                        + "; validadaPor="
                        + valueOrDash(
                            previousValidatedBy)
                        + "; activoRecibidoAnterior="
                        + previousAssetReceived
                        + "; inventarioActualizadoAnterior="
                        + previousInventoryUpdated
                        + "; referenciaInventarioAnterior="
                        + valueOrDash(
                            previousInventoryReference)
                        + "; motivo="
                        + reason
        );

        updateCaseStatus(
                task.getOffboardingCase());

        return toTaskResponse(task);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String cleanNullable(String value) {
        if (isBlank(value)) {
            return null;
        }

        return value.trim();
    }

    private String withReference(
            String taskName,
            String reference
    ) {
        String cleanReference =
                cleanNullable(reference);

        if (cleanReference == null) {
            return taskName;
        }

        String value =
                taskName + " — " + cleanReference;

        return value.length() <= 200
                ? value
                : value.substring(0, 200);
    }

    private String valueOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }

    private void updateCaseStatus(
            OffboardingCase offboardingCase
    ) {
        boolean allDone =
                offboardingCase.getTasks()
                        .stream()
                        .allMatch(this::isTaskClosed);

        CaseStatus newStatus =
                allDone
                        ? CaseStatus.COMPLETADA
                        : CaseStatus.EN_PROCESO;

        if (offboardingCase.getStatus()
                != newStatus) {
            offboardingCase.setStatus(newStatus);
            caseRepository.save(offboardingCase);

            auditService.record(
                    "SYSTEM",
                    "CASE_STATUS_CHANGED",
                    "OffboardingCase",
                    offboardingCase.getId(),
                    "nuevoEstado=" + newStatus
            );
        }
    }

    private boolean isAssetTask(
            OffboardingTask task
    ) {
        String systemName =
                task.getSystemName();

        return "Equipo de cómputo".equals(
                    systemName)
                || "Telefonía".equals(
                    systemName)
                || "Activos asignados".equals(
                    systemName)
                || "Telefonia".equals(
                    systemName);
    }

    private boolean isTaskClosed(
            OffboardingTask task
    ) {
        if (task.getStatus()
                == TaskStatus.NO_APLICA) {
            return true;
        }

        if (isAssetTask(task)) {
            return task.getStatus()
                    == TaskStatus.VALIDADA;
        }

        return task.getStatus()
                == TaskStatus.COMPLETADA
                || task.getStatus()
                == TaskStatus.VALIDADA;
    }


    private OffboardingCase requireCase(Long id) {
        return caseRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "No existe la baja con ID "
                                        + id
                        )
                );
    }

    private OffboardingTask requireTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "No existe la tarea con ID "
                                        + id
                        )
                );
    }

    private OffboardingResponse toResponse(
            OffboardingCase c
    ) {
        return new OffboardingResponse(
                c.getId(),
                c.getCaseNumber(),
                c.getEmployeeName(),
                c.getEmployeeIdentifier(),
                c.getCorporateEmail(),
                c.getDepartment(),
                c.getBuilding(),
                c.getWorkArea(),
                c.getManagerName(),
                c.getTerminationType(),
                c.getEffectiveAt(),
                c.getRiskLevel(),
                c.getStatus(),
                c.isConfidential(),
                c.isComputerAssigned(),
                c.getComputerDetails(),
                c.isPhoneAssigned(),
                c.getPhoneDetails(),
                c.isFingerprintRegistered(),
                c.isFaceidRegistered(),
                c.isEppAssigned(),
                c.isParkingAccess(),
                c.getEmployeeCategory(),
                c.getPhotoUrl(),
                c.getAccesses(),
                c.getOtherAccesses(),
                c.getRequestedBy(),
                c.getObservations(),
                c.getCreatedAt(),
                c.getTasks()
                        .stream()
                        .map(this::toTaskResponse)
                        .toList()
        );
    }

    private TaskResponse toTaskResponse(
            OffboardingTask task
    ) {
        return new TaskResponse(
                task.getId(),
                task.getSystemName(),
                task.getTaskName(),
                task.isCritical(),
                task.isAutomatic(),
                task.getDueAt(),
                task.getStatus(),
                task.getEvidenceReference(),
                task.getComments(),
                task.getCompletedAt(),
                task.getCompletedBy(),
                task.getValidatedAt(),
                task.getValidatedBy()
        );
    }
}
