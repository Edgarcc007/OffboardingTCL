-- UbicaciÃ³n fÃ­sica del escritorio del empleado.
--
-- department representa el departamento organizacional.
-- building y work_area representan la ubicaciÃ³n fÃ­sica.

ALTER TABLE offboarding_case
    ADD COLUMN building VARCHAR(100);

ALTER TABLE offboarding_case
    ADD COLUMN work_area VARCHAR(150);
