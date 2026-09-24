package com.empresa.offboarding.dto;

import java.time.OffsetDateTime;

public record NotificationRecipientResponse(
        Long id,
        String email,
        String description,
        boolean active,
        OffsetDateTime createdAt
) {}
