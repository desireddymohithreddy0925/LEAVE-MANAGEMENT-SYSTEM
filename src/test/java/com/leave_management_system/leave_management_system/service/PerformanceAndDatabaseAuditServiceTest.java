package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.ConnectionPoolMetricsDTO;
import com.leave_management_system.leave_management_system.dto.QueryPerformanceDiagnosticsDTO;
import com.leave_management_system.leave_management_system.dto.SystemPerformanceSummaryDTO;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PerformanceAndDatabaseAuditService Unit Tests")
class PerformanceAndDatabaseAuditServiceTest {

    @Mock
    private HikariDataSource hikariDataSource;

    @Mock
    private HikariPoolMXBean hikariPoolMXBean;

    @Mock
    private DataSource standardDataSource;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private ResultSet resultSet;

    @Test
    @DisplayName("getConnectionPoolMetrics returns accurate metrics for HikariDataSource")
    void getConnectionPoolMetrics_WithHikari_ReturnsAccurateMetrics() {
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(5);
        when(hikariPoolMXBean.getIdleConnections()).thenReturn(10);
        when(hikariPoolMXBean.getTotalConnections()).thenReturn(15);
        when(hikariPoolMXBean.getThreadsAwaitingConnection()).thenReturn(0);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(20);
        when(hikariDataSource.getMinimumIdle()).thenReturn(5);
        when(hikariDataSource.getConnectionTimeout()).thenReturn(20000L);
        when(hikariDataSource.getIdleTimeout()).thenReturn(600000L);
        when(hikariDataSource.getMaxLifetime()).thenReturn(1800000L);
        when(hikariDataSource.getLeakDetectionThreshold()).thenReturn(30000L);
        when(hikariDataSource.getPoolName()).thenReturn("HikariPool-Test");

        PerformanceAndDatabaseAuditService service = new PerformanceAndDatabaseAuditService(hikariDataSource, jdbcTemplate);
        ConnectionPoolMetricsDTO metrics = service.getConnectionPoolMetrics();

        assertThat(metrics).isNotNull();
        assertThat(metrics.getPoolName()).isEqualTo("HikariPool-Test");
        assertThat(metrics.getActiveConnections()).isEqualTo(5);
        assertThat(metrics.getIdleConnections()).isEqualTo(10);
        assertThat(metrics.getTotalConnections()).isEqualTo(15);
        assertThat(metrics.getThreadsAwaitingConnection()).isEqualTo(0);
        assertThat(metrics.getMaxPoolSize()).isEqualTo(20);
        assertThat(metrics.getPoolStatus()).isEqualTo("HEALTHY");
    }

    @Test
    @DisplayName("getConnectionPoolMetrics detects WARNING status under thread contention")
    void getConnectionPoolMetrics_WithContention_ReturnsWarning() {
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(18);
        when(hikariPoolMXBean.getIdleConnections()).thenReturn(2);
        when(hikariPoolMXBean.getTotalConnections()).thenReturn(20);
        when(hikariPoolMXBean.getThreadsAwaitingConnection()).thenReturn(2);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(20);

        PerformanceAndDatabaseAuditService service = new PerformanceAndDatabaseAuditService(hikariDataSource, jdbcTemplate);
        ConnectionPoolMetricsDTO metrics = service.getConnectionPoolMetrics();

        assertThat(metrics.getPoolStatus()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("getConnectionPoolMetrics falls back cleanly for non-Hikari DataSource")
    void getConnectionPoolMetrics_WithStandardDataSource_FallsBackCleanly() {
        PerformanceAndDatabaseAuditService service = new PerformanceAndDatabaseAuditService(standardDataSource, jdbcTemplate);
        ConnectionPoolMetricsDTO metrics = service.getConnectionPoolMetrics();

        assertThat(metrics).isNotNull();
        assertThat(metrics.getPoolName()).isEqualTo("StandardDataSource");
        assertThat(metrics.getPoolStatus()).isEqualTo("HEALTHY");
    }

    @Test
    @DisplayName("getQueryPerformanceDiagnostics extracts metadata and index information")
    void getQueryPerformanceDiagnostics_ExtractsIndexes() throws SQLException {
        when(hikariDataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getIndexInfo(isNull(), isNull(), anyString(), eq(false), eq(false))).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("INDEX_NAME")).thenReturn("idx_leave_request_emp");
        when(resultSet.getString("COLUMN_NAME")).thenReturn("employee_id");

        PerformanceAndDatabaseAuditService service = new PerformanceAndDatabaseAuditService(hikariDataSource, jdbcTemplate);
        QueryPerformanceDiagnosticsDTO diagnostics = service.getQueryPerformanceDiagnostics();

        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics.getStatus()).isEqualTo("OPTIMAL");
        assertThat(diagnostics.getMonitoredTablesCount()).isEqualTo(6);
        assertThat(diagnostics.getIndexesVerified()).isNotEmpty();
        assertThat(diagnostics.getNPlusOneProtections()).isNotEmpty();
        assertThat(diagnostics.getRecommendations()).isNotEmpty();
    }

    @Test
    @DisplayName("getSystemPerformanceSummary calculates JVM memory telemetry and SLAs")
    void getSystemPerformanceSummary_CalculatesMemoryAndSlas() {
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(2);

        PerformanceAndDatabaseAuditService service = new PerformanceAndDatabaseAuditService(hikariDataSource, jdbcTemplate);
        SystemPerformanceSummaryDTO summary = service.getSystemPerformanceSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.getAvailableProcessors()).isGreaterThan(0);
        assertThat(summary.getUsedMemoryMb()).isGreaterThan(0);
        assertThat(summary.getMaxMemoryMb()).isGreaterThan(0);
        assertThat(summary.getTargetReadSlaMs()).isEqualTo(150L);
        assertThat(summary.getTargetWriteSlaMs()).isEqualTo(250L);
        assertThat(summary.getActiveDatabaseConnections()).isEqualTo(2);
    }
}

