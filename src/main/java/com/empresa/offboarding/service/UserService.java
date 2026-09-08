package com.empresa.offboarding.service;

import com.empresa.offboarding.dto.CreateUserRequest;
import com.empresa.offboarding.dto.ResetUserPasswordRequest;
import com.empresa.offboarding.dto.UpdateUserRequest;
import com.empresa.offboarding.dto.UserResponse;
import com.empresa.offboarding.entity.AppUser;
import com.empresa.offboarding.enums.AppRole;
import com.empresa.offboarding.repository.AppUserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public UserResponse create(
            CreateUserRequest request,
            String actor
    ) {
        String username = request.username().trim();

        if (appUserRepository
                .existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException(
                    "Ya existe el usuario " + username
            );
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(
                request.fullName().trim());
        user.setEmail(
                normalizeOptionalEmail(request.email()));
        user.setPasswordHash(
                passwordEncoder.encode(
                        request.password()));
        user.setEnabled(true);
        user.setRoles(
                copyRoles(request.roles()));

        AppUser saved =
                appUserRepository.save(user);

        auditService.record(
                actor,
                "USER_CREATED",
                "AppUser",
                saved.getId(),
                "username=" + saved.getUsername()
                        + "; roles="
                        + saved.getRoles()
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return appUserRepository.findAll()
                .stream()
                .sorted(
                    Comparator.comparing(
                        AppUser::getUsername,
                        String.CASE_INSENSITIVE_ORDER
                    )
                )
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse update(
            Long userId,
            UpdateUserRequest request,
            String actor
    ) {
        AppUser user = requireUser(userId);

        Set<AppRole> requestedRoles =
                copyRoles(request.roles());

        if (actor.equalsIgnoreCase(
                    user.getUsername())
                && !request.enabled()) {
            throw new IllegalStateException(
                    "No puedes desactivar tu propia cuenta"
            );
        }

        if (actor.equalsIgnoreCase(
                    user.getUsername())
                && user.getRoles().contains(
                    AppRole.ADMIN)
                && !requestedRoles.contains(
                    AppRole.ADMIN)) {
            throw new IllegalStateException(
                    "No puedes retirar tu propio perfil ADMIN"
            );
        }

        protectLastAdministrator(
                user,
                request.enabled(),
                requestedRoles
        );

        String previousName =
                user.getFullName();
        String previousEmail =
                user.getEmail();
        boolean previousEnabled =
                user.isEnabled();
        Set<AppRole> previousRoles =
                Set.copyOf(user.getRoles());

        user.setFullName(
                request.fullName().trim());
        user.setEmail(
                normalizeOptionalEmail(request.email()));
        user.setEnabled(
                request.enabled());
        user.setRoles(requestedRoles);

        AppUser saved =
                appUserRepository.save(user);

        auditService.record(
                actor,
                "USER_UPDATED",
                "AppUser",
                saved.getId(),
                "username=" + saved.getUsername()
                        + "; nombreAnterior="
                        + previousName
                        + "; nombreNuevo="
                        + saved.getFullName()
                        + "; correoAnterior="
                        + previousEmail
                        + "; correoNuevo="
                        + saved.getEmail()
                        + "; estadoAnterior="
                        + previousEnabled
                        + "; estadoNuevo="
                        + saved.isEnabled()
                        + "; rolesAnteriores="
                        + previousRoles
                        + "; rolesNuevos="
                        + saved.getRoles()
        );

        return toResponse(saved);
    }

    @Transactional
    public void resetPassword(
            Long userId,
            ResetUserPasswordRequest request,
            String actor
    ) {
        AppUser user = requireUser(userId);

        if (passwordEncoder.matches(
                request.password(),
                user.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "La contraseña nueva debe ser diferente de la actual"
            );
        }

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.password()));

        appUserRepository.save(user);

        auditService.record(
                actor,
                "USER_PASSWORD_RESET",
                "AppUser",
                user.getId(),
                "username=" + user.getUsername()
        );
    }

    private void protectLastAdministrator(
            AppUser target,
            boolean newEnabled,
            Set<AppRole> newRoles
    ) {
        boolean currentlyActiveAdmin =
                target.isEnabled()
                        && target.getRoles()
                            .contains(AppRole.ADMIN);

        boolean remainsActiveAdmin =
                newEnabled
                        && newRoles.contains(
                            AppRole.ADMIN);

        if (!currentlyActiveAdmin
                || remainsActiveAdmin) {
            return;
        }

        long activeAdministrators =
                appUserRepository.findAll()
                        .stream()
                        .filter(AppUser::isEnabled)
                        .filter(user ->
                            user.getRoles().contains(
                                AppRole.ADMIN))
                        .count();

        if (activeAdministrators <= 1) {
            throw new IllegalStateException(
                    "No puedes desactivar o retirar el perfil "
                            + "del último administrador activo"
            );
        }
    }

    private Set<AppRole> copyRoles(
            Set<AppRole> roles
    ) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException(
                    "El usuario debe tener al menos un perfil"
            );
        }

        return EnumSet.copyOf(roles);
    }

    private AppUser requireUser(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() ->
                    new EntityNotFoundException(
                        "No existe el usuario con ID " + id
                    )
                );
    }

    private UserResponse toResponse(
            AppUser user
    ) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.isEnabled(),
                user.getRoles(),
                user.getCreatedAt()
        );
    }

    private String normalizeOptionalEmail(
            String email
    ) {
        if (email == null || email.isBlank()) {
            return null;
        }

        return email.trim();
    }

}
