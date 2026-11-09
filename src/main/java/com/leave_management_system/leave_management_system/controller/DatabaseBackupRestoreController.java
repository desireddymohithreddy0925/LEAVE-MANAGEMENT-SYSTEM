package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.DatabaseBackupResponseDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseBackupValidationDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseRestoreRequestDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseRestoreResponseDTO;
import com.leave_management_system.leave_management_system.service.DatabaseBackupRestoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller exposing administrative endpoints for database backup generation,
 * tamper detection / validation, and disaster recovery restoration.
 */
@RestController
@RequestMapping("/api/audit/database/backup")
@Tag(name = "Database Backup and Restore API", description = "Endpoints for managing database backups and disaster recovery")
@SecurityRequirement(name = "bearerAuth")
@org.springframework.validation.annotation.Validated
public class DatabaseBackupRestoreController {

    private final DatabaseBackupRestoreService backupRestoreService;

    public DatabaseBackupRestoreController(DatabaseBackupRestoreService backupRestoreService) {
        this.backupRestoreService = backupRestoreService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Generate a full database backup",
               description = "Creates an atomic SQL backup script with drop statements and computes a SHA-256 integrity hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Backup created successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<DatabaseBackupResponseDTO> createBackup(
            @RequestParam(required = false, defaultValue = "manual") String customName) {
        return ResponseEntity.ok(backupRestoreService.createBackup(customName));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "List all available backups",
               description = "Lists all SQL backups present in the backup storage directory with their metadata and SHA-256 hashes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved backup list"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<List<DatabaseBackupResponseDTO>> listBackups() {
        return ResponseEntity.ok(backupRestoreService.listBackups());
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Validate backup file integrity",
               description = "Inspects backup file existence, header structure, and SHA-256 hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Validation completed"),
            @ApiResponse(responseCode = "400", description = "Invalid filename or path traversal detected"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<DatabaseBackupValidationDTO> validateBackup(
            @RequestParam String fileName) {
        return ResponseEntity.ok(backupRestoreService.validateBackupFile(fileName));
    }

    @PostMapping("/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restore database from verified backup",
               description = "Restores database from specified backup script with optional SHA-256 tamper verification.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Database restored successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filename or restoration failure"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Master Admin privilege required")
    })
    public ResponseEntity<DatabaseRestoreResponseDTO> restoreBackup(
            @Valid @RequestBody DatabaseRestoreRequestDTO request) {
        return ResponseEntity.ok(backupRestoreService.restoreBackup(request.getFileName(), request.getExpectedSha256()));
    }
}

