package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.IdempotencyAuditReportDTO;
import com.leave_management_system.leave_management_system.dto.IdempotencyCleanupResponseDTO;
import com.leave_management_system.leave_management_system.dto.TransactionIntegrityAuditReportDTO;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.entity.LeaveRequestSignoff;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.repository.IdempotencyKeyRepository;
import com.leave_management_system.leave_management_system.repository.LeaveBalanceRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestSignoffRepository;
import com.leave_management_system.leave_management_system.repository.TransactionReviewRepository;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionAndIdempotencyAuditService Unit Tests")
class TransactionAndIdempotencyAuditServiceTest {

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private LeaveRequestSignoffRepository leaveRequestSignoffRepository;

    @Mock
    private TransactionReviewRepository transactionReviewRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private IdempotencyService idempotencyService;

    private TransactionAndIdempotencyAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new TransactionAndIdempotencyAuditService(
                leaveBalanceRepository,
                leaveRequestRepository,
                leaveRequestSignoffRepository,
                transactionReviewRepository,
                idempotencyKeyRepository,
                idempotencyService
        );
    }

    @Test
    @DisplayName("auditTransactionIntegrity returns HEALTHY when balance invariants and signoffs are consistent")
    void auditTransactionIntegrity_Healthy_WhenNoAnomalies() {
        Employee emp = new Employee();
        emp.setId(101L);

        LeaveType type = new LeaveType();
        type.setName("ANNUAL");

        LeaveBalance balance = new LeaveBalance();
        balance.setId(1L);
        balance.setEmployee(emp);
        balance.setLeaveType(type);
        balance.setYear(2026);
        balance.setTotal(20);
        balance.setUsed(5);
        balance.setAvailable(15);

        when(leaveBalanceRepository.findAll()).thenReturn(List.of(balance));
        when(leaveRequestRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionReviewRepository.count()).thenReturn(12L);

        TransactionIntegrityAuditReportDTO report = auditService.auditTransactionIntegrity();

        assertNotNull(report);
        assertEquals("HEALTHY", report.getOverallStatus());
        assertTrue(report.isInvariantsPreserved());
        assertTrue(report.isSignoffIntegrityPreserved());
        assertTrue(report.isOptimisticLockingAudited());
        assertEquals(1, report.getTotalBalanceRecordsAudited());
        assertEquals(0, report.getBalanceAnomaliesCount());
        assertEquals(0, report.getSignoffAnomaliesCount());
        assertEquals(12L, report.getTotalTransactionReviews());
    }

    @Test
    @DisplayName("auditTransactionIntegrity flags ANOMALY_DETECTED when balance equation (total == used + available) is violated")
    void auditTransactionIntegrity_DetectsBalanceAnomaly_WhenEquationViolated() {
        Employee emp = new Employee();
        emp.setId(102L);

        LeaveType type = new LeaveType();
        type.setName("SICK");

        LeaveBalance corruptBalance = new LeaveBalance();
        corruptBalance.setId(2L);
        corruptBalance.setEmployee(emp);
        corruptBalance.setLeaveType(type);
        corruptBalance.setYear(2026);
        corruptBalance.setTotal(20);
        corruptBalance.setUsed(5);
        corruptBalance.setAvailable(10); // total (20) != used (5) + available (10)

        when(leaveBalanceRepository.findAll()).thenReturn(List.of(corruptBalance));
        when(leaveRequestRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionReviewRepository.count()).thenReturn(3L);

        TransactionIntegrityAuditReportDTO report = auditService.auditTransactionIntegrity();

        assertNotNull(report);
        assertEquals("ANOMALY_DETECTED", report.getOverallStatus());
        assertFalse(report.isInvariantsPreserved());
        assertEquals(1, report.getBalanceAnomaliesCount());
        assertTrue(report.getBalanceAnomalies().getFirst().contains("total (20) != used (5) + available (10)"));
    }

    @Test
    @DisplayName("auditTransactionIntegrity flags ANOMALY_DETECTED when approved dual approval request has fewer than 2 signoffs")
    void auditTransactionIntegrity_DetectsSignoffAnomaly_WhenApprovedDualApprovalLacksSignoffs() {
        when(leaveBalanceRepository.findAll()).thenReturn(Collections.emptyList());

        LeaveRequest req = new LeaveRequest();
        req.setId(500L);
        req.setRequiresDualApproval(true);
        req.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestRepository.findAll()).thenReturn(List.of(req));
        when(leaveRequestSignoffRepository.findByLeaveRequestId(500L))
                .thenReturn(List.of(new LeaveRequestSignoff())); // Only 1 signoff

        when(transactionReviewRepository.count()).thenReturn(5L);

        TransactionIntegrityAuditReportDTO report = auditService.auditTransactionIntegrity();

        assertNotNull(report);
        assertEquals("ANOMALY_DETECTED", report.getOverallStatus());
        assertFalse(report.isSignoffIntegrityPreserved());
        assertEquals(1, report.getSignoffAnomaliesCount());
        assertTrue(report.getSignoffAnomalies().getFirst().contains("requires dual approval and is APPROVED, but only has 1 signoff"));
    }

    @Test
    @DisplayName("auditIdempotencyHealth returns HEALTHY when no orphaned keys exist and expired count is normal")
    void auditIdempotencyHealth_Healthy_WhenNoOrphanedOrExcessiveExpired() {
        when(idempotencyKeyRepository.count()).thenReturn(100L);
        when(idempotencyKeyRepository.countByStatus("COMPLETED")).thenReturn(80L);
        when(idempotencyKeyRepository.countByStatus("IN_PROGRESS")).thenReturn(5L);
        when(idempotencyKeyRepository.countByStatus("FAILED")).thenReturn(15L);
        when(idempotencyKeyRepository.countByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(10L);
        when(idempotencyService.getInProgressTimeoutSeconds()).thenReturn(120L);
        when(idempotencyKeyRepository.countByCreatedAtBeforeAndStatus(any(LocalDateTime.class), eq("IN_PROGRESS"))).thenReturn(0L);

        IdempotencyAuditReportDTO report = auditService.auditIdempotencyHealth();

        assertNotNull(report);
        assertEquals("HEALTHY", report.getStatus());
        assertEquals(100L, report.getTotalKeys());
        assertEquals(80L, report.getCompletedKeys());
        assertEquals(5L, report.getInProgressKeys());
        assertEquals(15L, report.getFailedKeys());
        assertEquals(10L, report.getExpiredKeys());
        assertEquals(0L, report.getOrphanedKeys());
        assertEquals(120L, report.getInProgressTimeoutSeconds());
    }

    @Test
    @DisplayName("auditIdempotencyHealth returns MAINTENANCE_REQUIRED when orphaned in-progress keys are detected")
    void auditIdempotencyHealth_MaintenanceRequired_WhenOrphanedKeysDetected() {
        when(idempotencyKeyRepository.count()).thenReturn(50L);
        when(idempotencyKeyRepository.countByStatus("COMPLETED")).thenReturn(30L);
        when(idempotencyKeyRepository.countByStatus("IN_PROGRESS")).thenReturn(15L);
        when(idempotencyKeyRepository.countByStatus("FAILED")).thenReturn(5L);
        when(idempotencyKeyRepository.countByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(5L);
        when(idempotencyService.getInProgressTimeoutSeconds()).thenReturn(120L);
        when(idempotencyKeyRepository.countByCreatedAtBeforeAndStatus(any(LocalDateTime.class), eq("IN_PROGRESS"))).thenReturn(3L);

        IdempotencyAuditReportDTO report = auditService.auditIdempotencyHealth();

        assertNotNull(report);
        assertEquals("MAINTENANCE_REQUIRED", report.getStatus());
        assertEquals(3L, report.getOrphanedKeys());
    }

    @Test
    @DisplayName("cleanupIdempotencyKeys calls idempotencyService to purge expired and interrupted keys")
    void cleanupIdempotencyKeys_SuccessfullyCleansUpAndReturnsDTO() {
        when(idempotencyService.cleanupExpiredKeys()).thenReturn(7);
        when(idempotencyService.getInProgressTimeoutSeconds()).thenReturn(120L);
        when(idempotencyService.recoverInterruptedKeys(120L)).thenReturn(2);

        IdempotencyCleanupResponseDTO response = auditService.cleanupIdempotencyKeys();

        assertNotNull(response);
        assertEquals(7, response.getDeletedExpiredCount());
        assertEquals(2, response.getDeletedInterruptedCount());
        assertTrue(response.getMessage().contains("Cleaned up 7 expired and 2 interrupted"));
        verify(idempotencyService, times(1)).cleanupExpiredKeys();
        verify(idempotencyService, times(1)).recoverInterruptedKeys(120L);
    }
}
