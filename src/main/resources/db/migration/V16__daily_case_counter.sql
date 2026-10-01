CREATE TABLE offboarding_case_daily_counter (
    day_key DATE PRIMARY KEY,
    last_value BIGINT NOT NULL,
    CONSTRAINT ck_offboarding_daily_counter_range
        CHECK (last_value BETWEEN 1 AND 999999)
);

INSERT INTO offboarding_case_daily_counter(day_key,last_value)
SELECT
    CAST(substring(case_number FROM 4 FOR 10) AS DATE),
    MAX(CAST(substring(case_number FROM 15 FOR 6) AS BIGINT))
FROM offboarding_case
WHERE case_number ~ '^OB-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{6}$'
  AND substring(case_number FROM 15 FOR 6) <> '000000'
GROUP BY CAST(substring(case_number FROM 4 FOR 10) AS DATE);