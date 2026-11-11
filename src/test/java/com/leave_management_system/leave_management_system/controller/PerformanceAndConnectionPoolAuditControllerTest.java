package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.ConnectionPoolMetricsDTO;
import com.leave_management_system.leave_management_system.dto.QueryPerformanceDiagnosticsDTO;
import com.leave_management_system.leave_management_system.dto.SystemPerformanceSummaryDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.service.AuditService;
import com.leave_management_system.leave_management_system.service.PerformanceAndDatabaseAuditService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PerformanceAndConnectionPoolAuditController Unit Tests")
class PerformanceAndConnectionPoolAuditControllerTest {

    @Mock
    private PerformanceAndDatabaseAuditService performanceAndDatabaseAuditService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PerformanceAndConnectionPoolAuditController performanceAndConnectionPoolAuditController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(performanceAndConnectionPoolAuditController)
                .setControllerAdvice(new GlobalExceptionHandler(auditService))
                .build();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("GET /api/audit/performance/pool-status returns HTTP 200 with Hikari pool metrics")
    void getConnectionPoolStatus_Returns200() throws Exception {
        ConnectionPoolMetricsDTO metrics = new ConnectionPoolMetricsDTO(
                "HikariPool-1", 4, 6, 10, 0, 20, 5, 20000L, 600000L, 1800000L, 30000L, "HEALTHY"
        );

        when(performanceAndDatabaseAuditService.getConnectionPoolMetrics()).thenReturn(metrics);

        mockMvc.perform(get("/api/audit/performance/pool-status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poolName").value("HikariPool-1"))
                .andExpect(jsonPath("$.activeConnections").value(4))
                .andExpect(jsonPath("$.idleConnections").value(6))
                .andExpect(jsonPath("$.totalConnections").value(10))
                .andExpect(jsonPath("$.threadsAwaitingConnection").value(0))
                .andExpect(jsonPath("$.maximumPoolSize").value(20))
                .andExpect(jsonPath("$.minimumIdle").value(5))
                .andExpect(jsonPath("$.connectionTimeoutMs").value(20000))
                .andExpect(jsonPath("$.poolStatus").value("HEALTHY"));

        verify(performanceAndDatabaseAuditService).getConnectionPoolMetrics();
    }

    @Test
    @DisplayName("GET /api/audit/performance/query-diagnostics returns HTTP 200 with index review")
    void getQueryDiagnostics_Returns200() throws Exception {
        QueryPerformanceDiagnosticsDTO diagnostics = new QueryPerformanceDiagnosticsDTO();
        diagnostics.setStatus("OPTIMAL");
        diagnostics.setMonitoredTablesCount(6);
        diagnostics.setTotalIndexesChecked(18);
        diagnostics.setIndexesVerified(List.of("leave_requests -> idx_leave_requests_employee (employee_id)"));
        diagnostics.setNPlusOneProtections(List.of("LeaveRequest queries utilize JOIN FETCH"));
        diagnostics.setRecommendations(List.of("Maintain connection pool maximum-pool-size between 10 and 30"));

        when(performanceAndDatabaseAuditService.getQueryPerformanceDiagnostics()).thenReturn(diagnostics);

        mockMvc.perform(get("/api/audit/performance/query-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPTIMAL"))
                .andExpect(jsonPath("$.monitoredTablesCount").value(6))
                .andExpect(jsonPath("$.totalIndexesChecked").value(18))
                .andExpect(jsonPath("$.indexesVerified[0]").value("leave_requests -> idx_leave_requests_employee (employee_id)"))
                .andExpect(jsonPath("$.nPlusOneProtections[0]").value("LeaveRequest queries utilize JOIN FETCH"));

        verify(performanceAndDatabaseAuditService).getQueryPerformanceDiagnostics();
    }

    @Test
    @DisplayName("GET /api/audit/performance/system-summary returns HTTP 200 with runtime metrics")
    void getSystemSummary_Returns200() throws Exception {
        SystemPerformanceSummaryDTO summary = new SystemPerformanceSummaryDTO(
                "OPTIMAL", 8, 1.45, 256.0, 2048.0, 12.5, 150L, 250L, 4
        );

        when(performanceAndDatabaseAuditService.getSystemPerformanceSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/audit/performance/system-summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPTIMAL"))
                .andExpect(jsonPath("$.availableProcessors").value(8))
                .andExpect(jsonPath("$.systemLoadAverage").value(1.45))
                .andExpect(jsonPath("$.usedMemoryMb").value(256.0))
                .andExpect(jsonPath("$.targetReadSlaMs").value(150))
                .andExpect(jsonPath("$.targetWriteSlaMs").value(250))
                .andExpect(jsonPath("$.activeDatabaseConnections").value(4));

        verify(performanceAndDatabaseAuditService).getSystemPerformanceSummary();
    }
}
