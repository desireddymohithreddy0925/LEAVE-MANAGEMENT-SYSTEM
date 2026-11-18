package com.leave_management_system.leave_management_system.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Enterprise Production PostgreSQL Engine Verification Test Suite:
 * Date and Concurrency Regression & Database/Transaction Integrity.
 *
 * Runs the full Priority 3 regression suite against the real PostgreSQL database engine (leave_management_test_pg)
 * to validate:
 * 1. PostgreSQL-specific date and timestamp handling across weekend and year-end boundaries.
 * 2. PostgreSQL row-level pessimistic locking (SELECT ... FOR UPDATE) under concurrent overlapping requests.
 * 3. PostgreSQL MVCC transaction isolation, atomicity, and rollback semantics.
 * 4. PostgreSQL unique constraints (UK_IDEMPOTENCY_KEY_USER, UK_LEAVE_BALANCE_EMP_TYPE_YEAR) under concurrency.
 * 5. Full Liquibase migration status and checksum integrity on PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles(profiles = "pgtest", inheritProfiles = false)
@Tag("postgres")
@DisplayName("Priority 4 — PostgreSQL Date, Concurrency & Database Integrity Regression Suite")
public class DateAndConcurrencyPostgresRegressionIntegrationTest extends DateAndConcurrencyRegressionIntegrationTest {

    @org.junit.jupiter.api.Test
    @DisplayName("PostgreSQL: Repeated Concurrency Execution (5 Iterations) - Verifies Zero Deadlocks and Invariant Preservation")
    public void testPostgres_RepeatedConcurrencyExecution_NoDeadlocksAndInvariantsStrictlyPreserved() throws Exception {
        for (int iteration = 1; iteration <= 5; iteration++) {
            setUp();
            testConcurrentApplications_SameEmployee_OverlappingDates_PreventsRaceCondition();
            testConcurrentApprovalAndCancellation_PreservesInvariantsAndPreventsInconsistentState();
            testSimultaneousBalanceUpdates_SerializesCorrectlyWithoutLostUpdates();
            cleanup();
        }
    }
}
