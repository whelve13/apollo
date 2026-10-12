package com.apollo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hibernate's {@code ddl-auto=update} adds new columns but never relaxes an existing NOT NULL constraint.
 * Databases created before lab results became optional-valued and patient-reportable still carry
 * NOT NULL on these two columns, which would reject text-only and patient-reported records.
 * Dropping NOT NULL is idempotent, so it is safe to run on every startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabResultSchemaMigration implements ApplicationRunner {

    private static final List<String> STATEMENTS = List.of(
            "ALTER TABLE lab_test_results ALTER COLUMN numeric_value DROP NOT NULL",
            "ALTER TABLE lab_test_results ALTER COLUMN doctor_id DROP NOT NULL"
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (String statement : STATEMENTS) {
            try {
                jdbcTemplate.execute(statement);
            } catch (DataAccessException e) {
                log.error("Lab result schema migration step failed ({}): {}", statement, e.getMessage());
            }
        }
    }
}
