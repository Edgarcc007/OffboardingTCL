package com.empresa.offboarding.enums;

/**
 * Estados de un caso de baja. Coinciden con
 * ck_offboarding_case_status de la migracion V1.
 */
public enum CaseStatus {

    /** Registrada, ninguna tarea atendida todavia. */
    PROGRAMADA,

    /** Al menos una tarea avanzo, pero faltan cierres. */
    EN_PROCESO,

    /** Todas las tareas cerradas segun su criticidad. */
    COMPLETADA,

    /** La baja se anulo antes de concluir. */
    CANCELADA
}