package com.leave_management_system.leave_management_system.controller;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Concurrency, Idempotency & Failure-Injection Integration Test Suite running against real PostgreSQL database.
 * Validates unique constraint conflict handling, row-level concurrency, post-commit replays,
 * failure injection, and interrupted operation recovery against the production database engine.
 */
@SpringBootTest
@ActiveProfiles(profiles = "pgtest", inheritProfiles = false)
@Tag("postgres")
public class IdempotencyPostgresIntegrationTest extends IdempotencyIntegrationTest {
}

