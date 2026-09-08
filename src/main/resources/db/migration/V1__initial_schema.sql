-- ============================================================
-- V1: Esquema inicial del sistema de bajas de usuarios
-- ============================================================

CREATE SEQUENCE offboarding_case_number_seq START WITH 1 INCREMENT BY 1;

-- ------------------------------------------------------------
-- Usuarios de la aplicacion
-- ------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(80)  NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    email         VARCHAR(150),
    password_hash VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

CREATE TABLE app_user_role (
    user_id BIGINT      NOT NULL,
    role    VARCHAR(40) NOT NULL,
    CONSTRAINT pk_app_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_app_user_role_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_app_user_role CHECK (
        role IN ('ADMIN', 'RECURSOS_HUMANOS', 'SEGURIDAD', 'EJECUTOR', 'AUDITOR')
    )
);

-- ------------------------------------------------------------
-- Catalogo de tareas
-- ------------------------------------------------------------
CREATE TABLE task_template (
    id          BIGSERIAL PRIMARY KEY,
    system_name VARCHAR(100) NOT NULL,
    task_name   VARCHAR(200) NOT NULL,
    critical    BOOLEAN      NOT NULL DEFAULT FALSE,
    automatic   BOOLEAN      NOT NULL DEFAULT FALSE,
    sla_hours   INTEGER      NOT NULL DEFAULT 24,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_task_template_sla CHECK (sla_hours > 0)
);

-- ------------------------------------------------------------
-- Casos de baja
-- ------------------------------------------------------------
CREATE TABLE offboarding_case (
    id                  BIGSERIAL PRIMARY KEY,
    case_number         VARCHAR(30)  NOT NULL,
    employee_name       VARCHAR(150) NOT NULL,
    employee_identifier VARCHAR(60)  NOT NULL,
    corporate_email     VARCHAR(150),
    department          VARCHAR(100),
    manager_name        VARCHAR(150),
    termination_type    VARCHAR(40)  NOT NULL,
    effective_at        TIMESTAMPTZ  NOT NULL,
    risk_level          VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    confidential        BOOLEAN      NOT NULL DEFAULT FALSE,
    requested_by        VARCHAR(80)  NOT NULL,
    observations        TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_offboarding_case_number UNIQUE (case_number),
    CONSTRAINT ck_offboarding_case_risk CHECK (
        risk_level IN ('BAJO', 'NORMAL', 'ALTO', 'CRITICO')
    ),
    CONSTRAINT ck_offboarding_case_status CHECK (
        status IN ('PROGRAMADA', 'EN_PROCESO', 'COMPLETADA', 'CANCELADA')
    )
);

CREATE INDEX idx_offboarding_case_status     ON offboarding_case (status);
CREATE INDEX idx_offboarding_case_identifier ON offboarding_case (employee_identifier);
CREATE INDEX idx_offboarding_case_created    ON offboarding_case (created_at DESC);

-- ------------------------------------------------------------
-- Tareas de cada caso
-- ------------------------------------------------------------
CREATE TABLE offboarding_task (
    id                  BIGSERIAL PRIMARY KEY,
    offboarding_case_id BIGINT       NOT NULL,
    system_name         VARCHAR(100) NOT NULL,
    task_name           VARCHAR(200) NOT NULL,
    critical            BOOLEAN      NOT NULL DEFAULT FALSE,
    automatic           BOOLEAN      NOT NULL DEFAULT FALSE,
    due_at              TIMESTAMPTZ,
    status              VARCHAR(20)  NOT NULL,
    evidence_reference  VARCHAR(200),
    comments            TEXT,
    completed_at        TIMESTAMPTZ,
    completed_by        VARCHAR(80),
    validated_at        TIMESTAMPTZ,
    validated_by        VARCHAR(80),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_offboarding_task_case
        FOREIGN KEY (offboarding_case_id) REFERENCES offboarding_case (id) ON DELETE CASCADE,
    CONSTRAINT ck_offboarding_task_status CHECK (
        status IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETADA', 'VALIDADA', 'NO_APLICA')
    ),
    CONSTRAINT ck_offboarding_task_segregation CHECK (
        validated_by IS NULL
        OR completed_by IS NULL
        OR LOWER(validated_by) <> LOWER(completed_by)
    )
);

CREATE INDEX idx_offboarding_task_case   ON offboarding_task (offboarding_case_id);
CREATE INDEX idx_offboarding_task_status ON offboarding_task (status);
CREATE INDEX idx_offboarding_task_due    ON offboarding_task (due_at);

-- ------------------------------------------------------------
-- Bitacora de auditoria
-- ------------------------------------------------------------
CREATE TABLE audit_event (
    id          BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor       VARCHAR(80) NOT NULL,
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id   BIGINT,
    details     TEXT
);

CREATE INDEX idx_audit_event_occurred ON audit_event (occurred_at DESC);
CREATE INDEX idx_audit_event_entity   ON audit_event (entity_type, entity_id);
CREATE INDEX idx_audit_event_actor    ON audit_event (actor);

-- ------------------------------------------------------------
-- Trigger: mantener updated_at
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_offboarding_case_updated_at
    BEFORE UPDATE ON offboarding_case
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ------------------------------------------------------------
-- Trigger: la bitacora es inmutable
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'La bitacora de auditoria es inmutable: no se permite % ', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_immutable
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_modification();

-- ------------------------------------------------------------
-- El usuario administrador inicial lo crea la aplicacion al arrancar (AdminBootstrap),
-- para que la contrasena quede cifrada por Spring y la creacion se registre en la bitacora.
