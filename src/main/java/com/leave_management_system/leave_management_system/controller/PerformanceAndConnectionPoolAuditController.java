package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.ConnectionPoolMetricsDTO;
import com.leave_management_system.leave_management_system.dto.QueryPerformanceDiagnosticsDTO;
import com.leave_management_system.leave_management_system.dto.SystemPerformanceSummaryDTO;
import com.leave_management_system.leave_management_system.service.PerformanceAndDatabaseAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller providing administrative audit endpoints for database connection pool telemetry,
 * query indexing diagnostics, and system latency and throughput health.
 */
@RestController
@RequestMapping("/api/audit/performance")
@Tag(name = "Performance & Connection Pool Audit API", description = "Endpoints for inspecting HikariCP connection pool health, query indexing diagnostics, and runtime telemetry")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PerformanceAndConnectionPoolAuditController {

    private final PerformanceAndDatabaseAuditService performanceAndDatabaseAuditService;

    public PerformanceAndConnectionPoolAuditController(PerformanceAndDatabaseAuditService performanceAndDatabaseAuditService) {
        this.performanceAndDatabaseAuditService = performanceAndDatabaseAuditService;
    }

    @GetMapping("/pool-status")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Audit HikariCP connection pool metrics",
               description = "Audits active, idle, total connections, threads awaiting connection, and pool sizing parameters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connection pool metrics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<ConnectionPoolMetricsDTO> getConnectionPoolStatus() {
        return ResponseEntity.ok(performanceAndDatabaseAuditService.getConnectionPoolMetrics());
    }

    @GetMapping("/query-diagnostics")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Audit database query performance and indexes",
               description = "Audits index presence across critical tables, N+1 prevention mechanisms, and query recommendations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Query performance diagnostics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<QueryPerformanceDiagnosticsDTO> getQueryDiagnostics() {
        return ResponseEntity.ok(performanceAndDatabaseAuditService.getQueryPerformanceDiagnostics());
    }

    @GetMapping("/system-summary")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Audit system runtime performance summary",
               description = "Audits JVM heap utilization, CPU cores, system load average, active connections, and SLA latency targets.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "System performance summary retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<SystemPerformanceSummaryDTO> getSystemSummary() {
        return ResponseEntity.ok(performanceAndDatabaseAuditService.getSystemPerformanceSummary());
    }
}

