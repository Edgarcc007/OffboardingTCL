package com.empresa.offboarding.dto;

import com.empresa.offboarding.enums.AppRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String fullName,

        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
        String email,

        boolean enabled,

        @NotEmpty(message = "Debes asignar al menos un perfil")
        Set<AppRole> roles
) {}
