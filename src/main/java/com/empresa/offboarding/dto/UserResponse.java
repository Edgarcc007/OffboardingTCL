package com.empresa.offboarding.dto;

import com.empresa.offboarding.enums.AppRole;

import java.time.OffsetDateTime;
import java.util.Set;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        boolean enabled,
        Set<AppRole> roles,
        OffsetDateTime createdAt
) {
    public UserResponse {
        roles = roles == null
                ? Set.of()
                : Set.copyOf(roles);
    }
}
