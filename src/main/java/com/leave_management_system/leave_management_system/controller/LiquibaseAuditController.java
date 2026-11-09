package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.LiquibaseAuditReportDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseChangesetDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseLockStatusDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseValidationResultDTO;
import com.leave_management_system.leave_management_system.service.LiquibaseMigrationAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller exposing administrative audit endpoints for Liquibase database migrations,
 * changelog lock inspection, and checksum consistency verification.
 */
@RestController
@RequestMapping("/api/audit/liquibase")
@Tag(name = "Liquibase Migration Audit API", description = "Endpoints for auditing Liquibase migrations, locks, and checksum consistency")
@SecurityRequirement(name = "bearerAuth")
@org.springframework.validation.annotation.Validated
public class LiquibaseAuditController {

    private final LiquibaseMigrationAuditService liquibaseMigrationAuditService;

    public LiquibaseAuditController(LiquibaseMigrationAuditService liquibaseMigrationAuditService) {
        this.liquibaseMigrationAuditService = liquibaseMigrationAuditService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Audit Liquibase migration status",
               description = "Returns complete migration audit including executed changesets, checksums, and lock state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit report successfully generated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<LiquibaseAuditReportDTO> getMigrationAuditReport() {
        return ResponseEntity.ok(liquibaseMigrationAuditService.auditMigrationStatus());
    }

    @GetMapping("/validate")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Validate changelog and checksum consistency",
               description = "Executes Liquibase validate to verify checksum integrity and detect drift.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Validation completed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<LiquibaseValidationResultDTO> validateChangelogs() {
        return ResponseEntity.ok(liquibaseMigrationAuditService.validateChangelogs());
    }

    @GetMapping("/changesets")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "List executed changesets",
               description = "Retrieves all changesets recorded in DATABASECHANGELOG in order of execution.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved changesets"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<List<LiquibaseChangesetDTO>> getExecutedChangesets() {
        return ResponseEntity.ok(liquibaseMigrationAuditService.getExecutedChangesets());
    }

    @GetMapping("/locks")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Inspect migration lock status",
               description = "Queries DATABASECHANGELOGLOCK to inspect current lock acquisition state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved lock status"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<LiquibaseLockStatusDTO> getLockStatus() {
        return ResponseEntity.ok(liquibaseMigrationAuditService.getLockStatus());
    }
}

