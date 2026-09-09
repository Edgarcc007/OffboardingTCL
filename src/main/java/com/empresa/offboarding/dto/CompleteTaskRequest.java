package com.empresa.offboarding.dto;


import jakarta.validation.constraints.Size;


public record CompleteTaskRequest(


        @Size(max = 200)
        String evidenceReference,


        @Size(max = 4000)
        String comments
) {


    public CompleteTaskRequest {
        if (evidenceReference != null) {
            evidenceReference = evidenceReference.trim();
            if (evidenceReference.isEmpty()) {
                evidenceReference = null;
            }
        }


        if (comments != null) {
            comments = comments.trim();
            if (comments.isEmpty()) {
                comments = null;
            }
        }
    }
}
