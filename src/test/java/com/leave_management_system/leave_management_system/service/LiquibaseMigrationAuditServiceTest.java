package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LiquibaseAuditReportDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseChangesetDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseLockStatusDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseValidationResultDTO;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LiquibaseMigrationAuditService Unit Tests")
class LiquibaseMigrationAuditServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private SpringLiquibase springLiquibase;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    private LiquibaseMigrationAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new LiquibaseMigrationAuditService(jdbcTemplate, dataSource, springLiquibase);
    }

    @Test
    @DisplayName("getExecutedChangesets maps database rows accurately")
    void getExecutedChangesets_MapsRowsSuccessfully() {
        LiquibaseChangesetDTO cs = new LiquibaseChangesetDTO(
                "001", "antigravity", "master.yaml", LocalDateTime.now(), 1,
                "EXECUTED", "9:abc1234==", "createTable", null, null, "5.0.3", "1"
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(cs));

        List<LiquibaseChangesetDTO> results = auditService.getExecutedChangesets();
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("001", results.get(0).getId());
        assertEquals("antigravity", results.get(0).getAuthor());
        assertEquals("9:abc1234==", results.get(0).getMd5sum());
    }

    @Test
    @DisplayName("getLockStatus returns unlocked state when table has resting record")
    void getLockStatus_ReturnsUnlockedState() {
        LiquibaseLockStatusDTO lockDTO = new LiquibaseLockStatusDTO(1, false, null, null, 1);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(lockDTO));

        LiquibaseLockStatusDTO result = auditService.getLockStatus();
        assertNotNull(result);
        assertEquals(1, result.getLockId());
        assertFalse(result.isLocked());
        assertNull(result.getLockGranted());
        assertNull(result.getLockedBy());
    }

    @Test
    @DisplayName("getLockStatus returns fallback default when lock table is empty")
    void getLockStatus_ReturnsDefaultWhenEmpty() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());

        LiquibaseLockStatusDTO result = auditService.getLockStatus();
        assertNotNull(result);
        assertEquals(1, result.getLockId());
        assertFalse(result.isLocked());
        assertEquals(0, result.getTotalLocks());
    }

    @Test
    @DisplayName("validateChangelogs handles database connection failure gracefully")
    void validateChangelogs_HandlesConnectionFailure() throws SQLException {
        when(springLiquibase.getChangeLog()).thenReturn("classpath:db/changelog/db.changelog-master.yaml");
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection pool exhausted"));

        LiquibaseValidationResultDTO result = auditService.validateChangelogs();
        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(1, result.getErrorCount());
        assertTrue(result.getMessage().contains("Connection pool exhausted"));
    }
}

