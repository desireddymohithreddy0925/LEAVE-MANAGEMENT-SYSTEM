package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LiquibaseAuditReportDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseChangesetDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseLockStatusDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseValidationResultDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.service.LiquibaseMigrationAuditService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiquibaseAuditController Unit Tests")
class LiquibaseAuditControllerTest {

    @Mock
    private LiquibaseMigrationAuditService liquibaseMigrationAuditService;

    @Mock
    private com.leave_management_system.leave_management_system.service.AuditService auditService;

    @InjectMocks
    private LiquibaseAuditController liquibaseAuditController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(liquibaseAuditController)
                .setControllerAdvice(new GlobalExceptionHandler(auditService))
                .build();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("GET /api/audit/liquibase/status returns HTTP 200 with complete report")
    void getMigrationAuditReport_Returns200() throws Exception {
        LiquibaseAuditReportDTO report = new LiquibaseAuditReportDTO();
        report.setOverallStatus("HEALTHY");
        report.setDatabaseProductName("PostgreSQL");
        report.setChangeLogFile("classpath:db/changelog/db.changelog-master.yaml");
        report.setTotalChangesetsExecuted(26);
        report.setUnrunChangesetsCount(0);
        report.setAllChecksumsPresent(true);
        report.setChecksumDriftDetected(false);
        report.setLockStatus(new LiquibaseLockStatusDTO(1, false, null, null, 1));

        LiquibaseChangesetDTO cs = new LiquibaseChangesetDTO("001", "author", "master.yaml",
                LocalDateTime.now(), 1, "EXECUTED", "9:abc1234==", "createTable", null, null, "5.0.3", "1234");
        report.setExecutedChangesets(List.of(cs));

        when(liquibaseMigrationAuditService.auditMigrationStatus()).thenReturn(report);

        mockMvc.perform(get("/api/audit/liquibase/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("HEALTHY"))
                .andExpect(jsonPath("$.databaseProductName").value("PostgreSQL"))
                .andExpect(jsonPath("$.totalChangesetsExecuted").value(26))
                .andExpect(jsonPath("$.allChecksumsPresent").value(true))
                .andExpect(jsonPath("$.lockStatus.locked").value(false))
                .andExpect(jsonPath("$.executedChangesets", hasSize(1)));

        verify(liquibaseMigrationAuditService, times(1)).auditMigrationStatus();
    }

    @Test
    @DisplayName("GET /api/audit/liquibase/validate returns HTTP 200 with validation result")
    void validateChangelogs_Returns200() throws Exception {
        LiquibaseValidationResultDTO result = new LiquibaseValidationResultDTO(true, 0, List.of(), List.of(),
                "All changelogs and checksums are consistent and validated successfully.");

        when(liquibaseMigrationAuditService.validateChangelogs()).thenReturn(result);

        mockMvc.perform(get("/api/audit/liquibase/validate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.message").value("All changelogs and checksums are consistent and validated successfully."));

        verify(liquibaseMigrationAuditService, times(1)).validateChangelogs();
    }

    @Test
    @DisplayName("GET /api/audit/liquibase/changesets returns HTTP 200 with executed changesets")
    void getExecutedChangesets_Returns200() throws Exception {
        LiquibaseChangesetDTO cs1 = new LiquibaseChangesetDTO("1", "antigravity", "master.yaml",
                LocalDateTime.now(), 1, "EXECUTED", "9:abc==", "createTable", null, null, "5.0.3", "1");
        LiquibaseChangesetDTO cs2 = new LiquibaseChangesetDTO("2", "antigravity", "master.yaml",
                LocalDateTime.now(), 2, "EXECUTED", "9:def==", "addColumn", null, null, "5.0.3", "1");

        when(liquibaseMigrationAuditService.getExecutedChangesets()).thenReturn(List.of(cs1, cs2));

        mockMvc.perform(get("/api/audit/liquibase/changesets")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[1].id").value("2"));

        verify(liquibaseMigrationAuditService, times(1)).getExecutedChangesets();
    }

    @Test
    @DisplayName("GET /api/audit/liquibase/locks returns HTTP 200 with lock status")
    void getLockStatus_Returns200() throws Exception {
        LiquibaseLockStatusDTO lockStatus = new LiquibaseLockStatusDTO(1, false, null, null, 1);

        when(liquibaseMigrationAuditService.getLockStatus()).thenReturn(lockStatus);

        mockMvc.perform(get("/api/audit/liquibase/locks")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockId").value(1))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.totalLocks").value(1));

        verify(liquibaseMigrationAuditService, times(1)).getLockStatus();
    }
}
