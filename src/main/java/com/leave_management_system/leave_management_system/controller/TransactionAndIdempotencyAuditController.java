package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.IdempotencyAuditReportDTO;
import com.leave_management_system.leave_management_system.dto.IdempotencyCleanupResponseDTO;
import com.leave_management_system.leave_management_system.dto.TransactionIntegrityAuditReportDTO;
import com.leave_management_system.leave_management_system.service.TransactionAndIdempotencyAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller providing administrative audit endpoints for database transaction integrity,
 * business balance invariants, optimistic locking, and idempotency key management.
 */
@RestController
@RequestMapping("/api/audit/transactions")
@Tag(name = "Transaction & Idempotency Audit API", description = "Endpoints for auditing transaction integrity, invariant preservation, and idempotency key lifecycle")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class TransactionAndIdempotencyAuditController {

    private final TransactionAndIdempotencyAuditService transactionAndIdempotencyAuditService;

    public TransactionAndIdempotencyAuditController(TransactionAndIdempotencyAuditService transactionAndIdempotencyAuditService) {
        this.transactionAndIdempotencyAuditService = transactionAndIdempotencyAuditService;
    }

    @GetMapping("/integrity")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Audit transaction integrity and database invariants",
               description = "Audits balance equation invariants (total = used + available), optimistic locking, and dual approval signoffs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction integrity audit generated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<TransactionIntegrityAuditReportDTO> getTransactionIntegrityReport() {
        return ResponseEntity.ok(transactionAndIdempotencyAuditService.auditTransactionIntegrity());
    }

    @GetMapping("/idempotency")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Audit idempotency key store health",
               description = "Audits active, completed, failed, expired, and orphaned idempotency keys.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Idempotency health audit report generated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<IdempotencyAuditReportDTO> getIdempotencyHealthReport() {
        return ResponseEntity.ok(transactionAndIdempotencyAuditService.auditIdempotencyHealth());
    }

    @PostMapping("/idempotency/cleanup")
    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('SYSTEM_AUDIT') or hasRole('ADMIN')")
    @Operation(summary = "Clean up expired and interrupted idempotency keys",
               description = "Manually triggers cleanup of expired keys and resets interrupted in-progress keys exceeding timeout.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Idempotency cleanup executed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin privilege required")
    })
    public ResponseEntity<IdempotencyCleanupResponseDTO> cleanupIdempotencyKeys() {
        return ResponseEntity.ok(transactionAndIdempotencyAuditService.cleanupIdempotencyKeys());
    }
}

