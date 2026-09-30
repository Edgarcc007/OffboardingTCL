package com.empresa.offboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetUserPasswordRequest(

        @NotBlank(message = "La contraseÃ±a es obligatoria")
        @Pattern(
                regexp = "^(?=\\S{8,100}$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*$",
                message = "La contraseÃ±a debe contener mayÃºscula, minÃºscula, nÃºmero y sÃ­mbolo"
        )
        String password
) {}
