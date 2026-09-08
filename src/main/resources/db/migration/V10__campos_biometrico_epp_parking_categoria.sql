-- V10: Campos adicionales de registro biométrico, EPP, estacionamiento, categoría y foto
ALTER TABLE offboarding_case ADD COLUMN fingerprint_registered BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE offboarding_case ADD COLUMN faceid_registered      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE offboarding_case ADD COLUMN epp_assigned           BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE offboarding_case ADD COLUMN parking_access         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE offboarding_case ADD COLUMN employee_category      VARCHAR(40);
ALTER TABLE offboarding_case ADD COLUMN photo_url              VARCHAR(500);
