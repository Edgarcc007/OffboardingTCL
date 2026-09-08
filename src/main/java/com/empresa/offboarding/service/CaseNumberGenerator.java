package com.empresa.offboarding.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates concurrent-safe monthly case numbers.
 *
 * Format:
 * OB-YYYY-MM-DD-NNNNNN
 *
 * The six-digit sequence restarts at 000001 every month.
 */
@Component
public class CaseNumberGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public String next() {
        String sql = """
                WITH business_date AS (
                    SELECT CURRENT_DATE AS value
                ),
                next_number AS (
                    INSERT INTO offboarding_case_monthly_counter (
                        month_key,
                        last_value
                    )
                    SELECT
                        TO_CHAR(value, 'YYYY-MM'),
                        1
                    FROM business_date
                    ON CONFLICT (month_key)
                    DO UPDATE SET
                        last_value =
                            offboarding_case_monthly_counter.last_value + 1
                    RETURNING
                        month_key,
                        last_value
                )
                SELECT
                    'OB-'
                    || TO_CHAR(business_date.value, 'YYYY-MM-DD')
                    || '-'
                    || LPAD(next_number.last_value::TEXT, 6, '0')
                FROM business_date
                CROSS JOIN next_number
                """;

        Object result = entityManager
                .createNativeQuery(sql)
                .getSingleResult();

        if (result == null) {
            throw new IllegalStateException(
                    "The next monthly case number could not be generated"
            );
        }

        return result.toString();
    }
}
