package com.empresa.offboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteTaskRequest(

        @NotBlank(message = "La referencia de evidencia es obligatoria")
        @Size(max = 200)
        String evidenceReference,

        @Size(max = 4000)
        String comments
) {

    public CompleteTaskRequest {
        if (evidenceReference != null) {
            evidenceReference = evidenceReference.trim();
        }

        if (comments != null) {
            comments = comments.trim();
            if (comments.isEmpty()) {
                comments = null;
            }
        }
    }
}