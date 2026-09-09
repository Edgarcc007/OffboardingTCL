package com.empresa.offboarding.dto;

import jakarta.validation.constraints.Size;

public record ValidateAssetTaskRequest(

        boolean assetReceived,

        boolean inventoryUpdated,

        @Size(max = 200)
        String inventoryReference,

        @Size(max = 2000)
        String validationComments
) {}
