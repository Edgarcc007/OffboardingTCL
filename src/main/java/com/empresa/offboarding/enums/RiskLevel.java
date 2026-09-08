package com.empresa.offboarding.enums;

/**
 * Nivel de riesgo de la baja. Determina la urgencia con la que
 * deben atenderse las tareas criticas. Coincide con
 * ck_offboarding_case_risk de la migracion V1.
 */
public enum RiskLevel {

    /** Salida ordinaria, sin accesos sensibles. */
    BAJO,

    /** Caso estandar. */
    NORMAL,

    /** Accesos privilegiados o informacion sensible. */
    ALTO,

    /** Despido conflictivo o administrador de sistemas. */
    CRITICO;
}