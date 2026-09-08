package com.empresa.offboarding.service;

import com.empresa.offboarding.entity.AuditEvent;
import com.empresa.offboarding.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_DETAILS_LENGTH = 4000;

    private final AuditEventRepository auditEventRepository;

    /**
     * Registra un hecho en la bitacora. La tabla tiene un trigger
     * que impide modificar o borrar estos registros.
     */
    @Transactional
    public void record(String actor,
                       String action,
                       String entityType,
                       Long entityId,
                       String details) {

        AuditEvent event = new AuditEvent();
        event.setActor(normalizeText(actor, "DESCONOCIDO"));
        event.setAction(normalizeText(action, "SIN_ACCION"));
        event.setEntityType(normalizeText(entityType, "SIN_ENTIDAD"));
        event.setEntityId(entityId);
        event.setDetails(truncate(details));

        auditEventRepository.save(event);
    }

    private String normalizeText(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? defaultValue : normalized;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_DETAILS_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_DETAILS_LENGTH);
    }
}