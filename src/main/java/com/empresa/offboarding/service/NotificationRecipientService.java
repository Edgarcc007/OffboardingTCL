package com.empresa.offboarding.service;

import com.empresa.offboarding.dto.NotificationRecipientRequest;
import com.empresa.offboarding.dto.NotificationRecipientResponse;
import com.empresa.offboarding.entity.NotificationRecipient;
import com.empresa.offboarding.repository.NotificationRecipientRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationRecipientService {

    private final NotificationRecipientRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<NotificationRecipientResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String getActiveRecipientsAsString() {
        List<NotificationRecipient> activos =
                repository.findByActiveTrueOrderByEmailAsc();

        if (activos.isEmpty()) {
            return null;
        }

        return activos.stream()
                .map(NotificationRecipient::getEmail)
                .collect(Collectors.joining(","));
    }

    @Transactional
    public NotificationRecipientResponse create(
            NotificationRecipientRequest request,
            String actor
    ) {
        String email = request.email().trim().toLowerCase();

        if (repository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException(
                    "The recipient " + email + " already exists"
            );
        }

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setEmail(email);
        recipient.setDescription(
                request.description() != null
                        ? request.description().trim()
                        : null
        );
        recipient.setActive(request.active());

        NotificationRecipient saved = repository.save(recipient);

        auditService.record(
                actor,
                "RECIPIENT_CREATED",
                "NotificationRecipient",
                saved.getId(),
                "email=" + saved.getEmail()
                        + "; description="
                        + (saved.getDescription() != null
                                ? saved.getDescription()
                                : "-")
        );

        return toResponse(saved);
    }

    @Transactional
    public NotificationRecipientResponse update(
            Long id,
            NotificationRecipientRequest request,
            String actor
    ) {
        NotificationRecipient recipient = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Recipient not found with ID " + id
                ));

        String previousEmail = recipient.getEmail();
        boolean previousActive = recipient.isActive();

        String newEmail = request.email().trim().toLowerCase();

        if (!newEmail.equalsIgnoreCase(previousEmail)
                && repository.existsByEmailIgnoreCase(newEmail)) {
            throw new IllegalStateException(
                    "The recipient " + newEmail + " already exists"
            );
        }

        recipient.setEmail(newEmail);
        recipient.setDescription(
                request.description() != null
                        ? request.description().trim()
                        : null
        );
        recipient.setActive(request.active());

        NotificationRecipient saved = repository.save(recipient);

        auditService.record(
                actor,
                "RECIPIENT_UPDATED",
                "NotificationRecipient",
                saved.getId(),
                "emailAnterior=" + previousEmail
                        + "; emailNuevo=" + saved.getEmail()
                        + "; activoAnterior=" + previousActive
                        + "; activoNuevo=" + saved.isActive()
        );

        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id, String actor) {
        NotificationRecipient recipient = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Recipient not found with ID " + id
                ));

        repository.delete(recipient);

        auditService.record(
                actor,
                "RECIPIENT_DELETED",
                "NotificationRecipient",
                id,
                "email=" + recipient.getEmail()
        );
    }

    private NotificationRecipientResponse toResponse(
            NotificationRecipient r
    ) {
        return new NotificationRecipientResponse(
                r.getId(),
                r.getEmail(),
                r.getDescription(),
                r.isActive(),
                r.getCreatedAt()
        );
    }
}
