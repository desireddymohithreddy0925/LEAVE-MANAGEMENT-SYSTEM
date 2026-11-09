package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.IdempotencyAuditReportDTO;
import com.leave_management_system.leave_management_system.dto.IdempotencyCleanupResponseDTO;
import com.leave_management_system.leave_management_system.dto.TransactionIntegrityAuditReportDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.service.AuditService;
import com.leave_management_system.leave_management_system.service.TransactionAndIdempotencyAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionAndIdempotencyAuditController Unit Tests")
class TransactionAndIdempotencyAuditControllerTest {

    @Mock
    private TransactionAndIdempotencyAuditService transactionAndIdempotencyAuditService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private TransactionAndIdempotencyAuditController transactionAndIdempotencyAuditController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(transactionAndIdempotencyAuditController)
                .setControllerAdvice(new GlobalExceptionHandler(auditService))
                .build();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("GET /api/audit/transactions/integrity returns HTTP 200 with report")
    void getTransactionIntegrityReport_Returns200() throws Exception {
        TransactionIntegrityAuditReportDTO report = new TransactionIntegrityAuditReportDTO();
        report.setOverallStatus("HEALTHY");
        report.setInvariantsPreserved(true);
        report.setTotalBalanceRecordsAudited(45);
        report.setBalanceAnomaliesCount(0);
        report.setSignoffIntegrityPreserved(true);
        report.setSignoffAnomaliesCount(0);
        report.setOptimisticLockingAudited(true);
        report.setTotalTransactionReviews(10);

        when(transactionAndIdempotencyAuditService.auditTransactionIntegrity()).thenReturn(report);

        mockMvc.perform(get("/api/audit/transactions/integrity")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("HEALTHY"))
                .andExpect(jsonPath("$.invariantsPreserved").value(true))
                .andExpect(jsonPath("$.totalBalanceRecordsAudited").value(45))
                .andExpect(jsonPath("$.balanceAnomaliesCount").value(0))
                .andExpect(jsonPath("$.signoffIntegrityPreserved").value(true))
                .andExpect(jsonPath("$.optimisticLockingAudited").value(true))
                .andExpect(jsonPath("$.totalTransactionReviews").value(10));

        verify(transactionAndIdempotencyAuditService).auditTransactionIntegrity();
    }

    @Test
    @DisplayName("GET /api/audit/transactions/idempotency returns HTTP 200 with health metrics")
    void getIdempotencyHealthReport_Returns200() throws Exception {
        IdempotencyAuditReportDTO report = new IdempotencyAuditReportDTO(
                "HEALTHY", 120L, 100L, 5L, 15L, 8L, 0L, 120L
        );

        when(transactionAndIdempotencyAuditService.auditIdempotencyHealth()).thenReturn(report);

        mockMvc.perform(get("/api/audit/transactions/idempotency")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.totalKeys").value(120))
                .andExpect(jsonPath("$.completedKeys").value(100))
                .andExpect(jsonPath("$.inProgressKeys").value(5))
                .andExpect(jsonPath("$.failedKeys").value(15))
                .andExpect(jsonPath("$.expiredKeys").value(8))
                .andExpect(jsonPath("$.orphanedKeys").value(0))
                .andExpect(jsonPath("$.inProgressTimeoutSeconds").value(120));

        verify(transactionAndIdempotencyAuditService).auditIdempotencyHealth();
    }

    @Test
    @DisplayName("POST /api/audit/transactions/idempotency/cleanup returns HTTP 200 with cleanup count")
    void cleanupIdempotencyKeys_Returns200() throws Exception {
        IdempotencyCleanupResponseDTO response = new IdempotencyCleanupResponseDTO(
                12, 1, "Cleaned up 12 expired and 1 interrupted idempotency keys."
        );

        when(transactionAndIdempotencyAuditService.cleanupIdempotencyKeys()).thenReturn(response);

        mockMvc.perform(post("/api/audit/transactions/idempotency/cleanup")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedExpiredCount").value(12))
                .andExpect(jsonPath("$.deletedInterruptedCount").value(1))
                .andExpect(jsonPath("$.message").value("Cleaned up 12 expired and 1 interrupted idempotency keys."));

        verify(transactionAndIdempotencyAuditService).cleanupIdempotencyKeys();
    }
}

