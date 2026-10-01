ALTER TABLE offboarding_task
    ADD COLUMN workflow_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN admin_self_validation BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN admin_validation_user_id BIGINT,
    ADD COLUMN admin_validation_operation UUID,
    ADD COLUMN admin_validation_request_hash VARCHAR(64);

ALTER TABLE offboarding_task
    DROP CONSTRAINT ck_offboarding_task_segregation;

ALTER TABLE offboarding_task
    ADD CONSTRAINT ck_offboarding_task_segregation CHECK (
        validated_by IS NULL
        OR completed_by IS NULL
        OR LOWER(validated_by) <> LOWER(completed_by)
        OR admin_self_validation
    );

ALTER TABLE offboarding_task
    ADD CONSTRAINT ck_offboarding_admin_validation_certificate CHECK (
        (
            admin_self_validation = FALSE
            AND admin_validation_user_id IS NULL
            AND admin_validation_operation IS NULL
            AND admin_validation_request_hash IS NULL
        )
        OR
        (
            admin_self_validation = TRUE
            AND admin_validation_user_id IS NOT NULL
            AND admin_validation_operation IS NOT NULL
            AND admin_validation_request_hash IS NOT NULL
            AND admin_validation_request_hash ~ '^[0-9a-f]{64}$'
            AND completed_by IS NOT NULL
            AND validated_by IS NOT NULL
            AND LOWER(completed_by) = LOWER(validated_by)
            AND completed_at IS NOT NULL
            AND validated_at IS NOT NULL
            AND status = 'VALIDADA'
            AND asset_received = TRUE
            AND inventory_updated = TRUE
        )
    );

CREATE UNIQUE INDEX ux_offboarding_admin_validation_operation
    ON offboarding_task(admin_validation_operation)
    WHERE admin_validation_operation IS NOT NULL;

CREATE FUNCTION guard_offboarding_admin_validation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    certificate_changed BOOLEAN;
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.workflow_revision := 0;
    ELSE
        NEW.workflow_revision := OLD.workflow_revision + 1;
    END IF;

    -- Reabrir elimina la autorizacion del ciclo anterior.
    IF NEW.validated_by IS NULL OR NEW.validated_at IS NULL THEN
        NEW.admin_self_validation := FALSE;
        NEW.admin_validation_user_id := NULL;
        NEW.admin_validation_operation := NULL;
        NEW.admin_validation_request_hash := NULL;
    END IF;

    IF NEW.admin_self_validation THEN
        certificate_changed := TRUE;

        IF TG_OP = 'UPDATE' THEN
            certificate_changed :=
                ROW(
                    NEW.admin_self_validation,
                    NEW.admin_validation_user_id,
                    NEW.admin_validation_operation,
                    NEW.admin_validation_request_hash,
                    NEW.completed_by,
                    NEW.completed_at,
                    NEW.validated_by,
                    NEW.validated_at
                )
                IS DISTINCT FROM
                ROW(
                    OLD.admin_self_validation,
                    OLD.admin_validation_user_id,
                    OLD.admin_validation_operation,
                    OLD.admin_validation_request_hash,
                    OLD.completed_by,
                    OLD.completed_at,
                    OLD.validated_by,
                    OLD.validated_at
                );
        END IF;

        IF certificate_changed THEN
            IF current_setting('offboarding.admin_validation_actor',TRUE)
                    IS DISTINCT FROM NEW.admin_validation_user_id::TEXT
               OR current_setting('offboarding.admin_validation_task',TRUE)
                    IS DISTINCT FROM NEW.id::TEXT THEN
                RAISE EXCEPTION 'Missing administrative validation context'
                    USING ERRCODE = '42501';
            END IF;

            PERFORM u.id
            FROM app_user u
            JOIN app_user_role r
              ON r.user_id=u.id AND r.role='ADMIN'
            WHERE u.id=NEW.admin_validation_user_id
              AND u.enabled=TRUE
              AND u.username=NEW.validated_by
            FOR SHARE OF u,r;

            IF NOT FOUND THEN
                RAISE EXCEPTION 'Only an enabled ADMIN can self-validate'
                    USING ERRCODE = '42501';
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_offboarding_admin_validation
    BEFORE INSERT OR UPDATE ON offboarding_task
    FOR EACH ROW
    EXECUTE FUNCTION guard_offboarding_admin_validation();