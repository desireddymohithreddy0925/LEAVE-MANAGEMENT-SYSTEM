package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.AuditLogResponseDTO;
import com.leave_management_system.leave_management_system.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit API", description = "Endpoints for viewing audit and security logs")
@org.springframework.validation.annotation.Validated
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")
    @GetMapping
    @Operation(summary = "Search audit logs", description = "Search audit logs with filtering and pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    })
    public Page<AuditLogResponseDTO> searchAuditLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.searchAuditLogs(userId, action, entityType, entityId, startDate, endDate, pageable);
    }

    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")
    @GetMapping("/{id}")
    @Operation(summary = "Get audit log by ID", description = "Retrieve a single audit log record by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved audit log"),
            @ApiResponse(responseCode = "404", description = "Audit log not found")
    })
    public AuditLogResponseDTO getAuditLogById(@PathVariable Long id) {
        return auditService.getAuditLogById(id);
    }

    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")
    @GetMapping("/entity/{entityType}/{entityId}")
    @Operation(summary = "Get audit logs for entity", description = "Retrieve audit log trail for a specific entity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved audit logs")
    })
    public Page<AuditLogResponseDTO> getAuditLogsByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.getAuditLogsByEntity(entityType, entityId, pageable);
    }
}
