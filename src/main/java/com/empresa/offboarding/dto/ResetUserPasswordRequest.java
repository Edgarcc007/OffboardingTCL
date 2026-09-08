package com.empresa.offboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetUserPasswordRequest(

        @NotBlank(message = "La contraseña es obligatoria")
        @Pattern(
                regexp = "^(?=\\S{12,100}$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*$",
                message = "La contraseña debe contener mayúscula, minúscula, número y símbolo"
        )
        String password
) {}
