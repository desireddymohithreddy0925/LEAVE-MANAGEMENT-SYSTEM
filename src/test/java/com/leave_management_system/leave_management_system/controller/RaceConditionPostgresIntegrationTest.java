package com.leave_management_system.leave_management_system.controller;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Concurrency & Race-Condition Integration Test Suite running against real PostgreSQL database.
 * Validates row-level pessimistic locking, transaction isolation, deadlock avoidance,
 * and accounting invariants under high concurrent load against the production database engine.
 */
@SpringBootTest
@ActiveProfiles(profiles = "pgtest", inheritProfiles = false)
@Tag("postgres")
public class RaceConditionPostgresIntegrationTest extends RaceConditionIntegrationTest {
}
