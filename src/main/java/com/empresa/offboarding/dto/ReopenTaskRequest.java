package com.empresa.offboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud administrativa para reabrir una tarea procesada.
 */
public record ReopenTaskRequest(

        @NotBlank(message = "El motivo de reapertura es obligatorio")
        @Size(
                min = 5,
                max = 500,
                message = "El motivo debe contener entre 5 y 500 caracteres"
        )
        String reason
) {
}
