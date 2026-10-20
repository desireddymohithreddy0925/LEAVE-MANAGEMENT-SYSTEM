package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.AuthorizationAuditResponseDTO;
import com.leave_management_system.leave_management_system.dto.AuthorizationSummaryDTO;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/authorization-audits")
@Tag(name = "Authorization Auditing API", description = "Endpoints for viewing authorization decisions, policy changes, and access control audit logs")
@org.springframework.validation.annotation.Validated
public class AuthorizationAuditController {

    private final AuthorizationAuditService authorizationAuditService;

    public AuthorizationAuditController(AuthorizationAuditService authorizationAuditService) {
        this.authorizationAuditService = authorizationAuditService;
    }

    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")
    @GetMapping
    @Operation(summary = "Search authorization audit logs", description = "Search and filter authorization decision audits with multi-dimensional criteria.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved authorization audit logs")
    })
    public Page<AuthorizationAuditResponseDTO> searchAuthorizationAudits(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return authorizationAuditService.searchAuthorizationAudits(userId, email, action, resource, decision, startDate, endDate, pageable);
    }

    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")
    @GetMapping("/summary")
    @Operation(summary = "Get authorization summary metrics", description = "Retrieve aggregate metrics including total decisions, grant count, denial count, and denial rate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved summary metrics")
    })
    public AuthorizationSummaryDTO getSummaryMetrics() {
        return authorizationAuditService.getSummaryMetrics();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    @Operation(summary = "Get current user's authorization audits", description = "Retrieve self-service authorization audits for the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user authorization audits")
    })
    public Page<AuthorizationAuditResponseDTO> getMyAuthorizationAudits(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return authorizationAuditService.getAuditsForCurrentUser(userDetails, pageable);
    }

    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get authorization audits for user", description = "Retrieve authorization decision history for a specific user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user authorization audits")
    })
    public Page<AuthorizationAuditResponseDTO> getAuthorizationAuditsForUser(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return authorizationAuditService.getAuditsForUser(userId, pageable);
    }

    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")
    @GetMapping("/{id}")
    @Operation(summary = "Get authorization audit log by ID", description = "Retrieve a single authorization audit log record by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved authorization audit log"),
            @ApiResponse(responseCode = "404", description = "Authorization audit log not found")
    })
    public AuthorizationAuditResponseDTO getAuthorizationAuditById(@PathVariable Long id) {
        return authorizationAuditService.getAuditById(id);
    }
}
