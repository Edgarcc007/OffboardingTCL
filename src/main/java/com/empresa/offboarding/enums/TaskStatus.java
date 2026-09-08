package com.empresa.offboarding.enums;

/**
 * Estados de una tarea individual. Coinciden con
 * ck_offboarding_task_status de la migracion V1.
 */
public enum TaskStatus {

    /** Generada, sin atender. */
    PENDIENTE,

    /** El ejecutor la tomo pero no la ha cerrado. */
    EN_PROCESO,

    /** Ejecutada y con evidencia registrada. */
    COMPLETADA,

    /** Revisada por una segunda persona. Estado final de las criticas. */
    VALIDADA,

    /** El empleado no tenia acceso a ese sistema. */
    NO_APLICA;
}