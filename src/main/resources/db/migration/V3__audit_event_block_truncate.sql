-- Un trigger BEFORE DELETE de fila NO se dispara con TRUNCATE.
-- Sin esta proteccion, la bitacora se podria vaciar de un solo golpe.
CREATE OR REPLACE FUNCTION fn_audit_event_block_truncate()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'La bitacora de auditoria es inmutable: no se permite TRUNCATE';
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_event_no_truncate ON audit_event;

CREATE TRIGGER trg_audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT
    EXECUTE FUNCTION fn_audit_event_block_truncate();
