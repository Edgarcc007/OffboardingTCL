package com.empresa.offboarding.dto;

import com.empresa.offboarding.enums.AppRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(

        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 80, message = "El usuario no puede exceder 80 caracteres")
        @Pattern(
                regexp = "^[A-Za-z0-9._-]+$",
                message = "El usuario contiene caracteres no permitidos"
        )
        String username,

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
        String fullName,

        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede exceder 150 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Pattern(
                regexp = "^(?=\\S{12,100}$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*$",
                message = "Debe tener entre 12 y 100 caracteres, mayúscula, minúscula, número y símbolo"
        )
        String password,

        @NotEmpty(message = "Debes asignar al menos un perfil")
        Set<AppRole> roles
) {}
