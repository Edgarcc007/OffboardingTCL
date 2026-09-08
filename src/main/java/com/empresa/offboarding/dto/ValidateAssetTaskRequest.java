package com.empresa.offboarding.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ValidateAssetTaskRequest(

        @AssertTrue(
                message = "Debes confirmar la recepción física del activo"
        )
        boolean assetReceived,

        @AssertTrue(
                message = "Debes confirmar la actualización del inventario"
        )
        boolean inventoryUpdated,

        @NotBlank(
                message = "La referencia de inventario es obligatoria"
        )
        @Size(max = 200)
        String inventoryReference,

        @Size(max = 2000)
        String validationComments
) {}
