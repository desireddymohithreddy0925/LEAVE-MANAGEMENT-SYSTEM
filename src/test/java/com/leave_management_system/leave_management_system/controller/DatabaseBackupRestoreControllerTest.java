package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.DatabaseBackupResponseDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseBackupValidationDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseRestoreRequestDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseRestoreResponseDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.service.AuditService;
import com.leave_management_system.leave_management_system.service.DatabaseBackupRestoreService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseBackupRestoreController Unit Tests")
class DatabaseBackupRestoreControllerTest {

    @Mock
    private DatabaseBackupRestoreService backupRestoreService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DatabaseBackupRestoreController backupRestoreController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(backupRestoreController)
                .setControllerAdvice(new GlobalExceptionHandler(auditService))
                .build();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /api/audit/database/backup/create returns HTTP 200 with generated backup details")
    void createBackup_Returns200() throws Exception {
        DatabaseBackupResponseDTO response = new DatabaseBackupResponseDTO(
                "backup_test.sql", "/path/to/backup_test.sql", 1024L,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                LocalDateTime.now(), "H2", "SUCCESS"
        );

        when(backupRestoreService.createBackup("unit_test")).thenReturn(response);

        mockMvc.perform(post("/api/audit/database/backup/create")
                        .param("customName", "unit_test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("backup_test.sql"))
                .andExpect(jsonPath("$.fileSizeBytes").value(1024))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(backupRestoreService, times(1)).createBackup("unit_test");
    }

    @Test
    @DisplayName("GET /api/audit/database/backup/list returns HTTP 200 with list of backups")
    void listBackups_Returns200() throws Exception {
        DatabaseBackupResponseDTO b1 = new DatabaseBackupResponseDTO(
                "b1.sql", "/b1.sql", 500L, "hash1", LocalDateTime.now(), "H2", "AVAILABLE");
        DatabaseBackupResponseDTO b2 = new DatabaseBackupResponseDTO(
                "b2.sql", "/b2.sql", 700L, "hash2", LocalDateTime.now(), "H2", "AVAILABLE");

        when(backupRestoreService.listBackups()).thenReturn(List.of(b1, b2));

        mockMvc.perform(get("/api/audit/database/backup/list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fileName").value("b1.sql"))
                .andExpect(jsonPath("$[1].fileName").value("b2.sql"));

        verify(backupRestoreService, times(1)).listBackups();
    }

    @Test
    @DisplayName("POST /api/audit/database/backup/validate returns HTTP 200 with validation result")
    void validateBackup_Returns200() throws Exception {
        DatabaseBackupValidationDTO validation = new DatabaseBackupValidationDTO(
                "valid.sql", true, 2048L, "hashValid", "Validation successful", List.of());

        when(backupRestoreService.validateBackupFile("valid.sql")).thenReturn(validation);

        mockMvc.perform(post("/api/audit/database/backup/validate")
                        .param("fileName", "valid.sql")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("valid.sql"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.fileSizeBytes").value(2048));

        verify(backupRestoreService, times(1)).validateBackupFile("valid.sql");
    }

    @Test
    @DisplayName("POST /api/audit/database/backup/restore returns HTTP 200 with restoration response")
    void restoreBackup_Returns200() throws Exception {
        DatabaseRestoreRequestDTO request = new DatabaseRestoreRequestDTO("restore.sql", "validHash");
        DatabaseRestoreResponseDTO response = new DatabaseRestoreResponseDTO(
                "restore.sql", "SUCCESS", LocalDateTime.now(), "validHash", "Restored successfully");

        when(backupRestoreService.restoreBackup(eq("restore.sql"), eq("validHash"))).thenReturn(response);

        mockMvc.perform(post("/api/audit/database/backup/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("restore.sql"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.sha256Checksum").value("validHash"));

        verify(backupRestoreService, times(1)).restoreBackup(eq("restore.sql"), eq("validHash"));
    }
}

