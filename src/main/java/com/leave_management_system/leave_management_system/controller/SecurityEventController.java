package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.SecurityEventResponseDTO;
import com.leave_management_system.leave_management_system.dto.SecurityEventSummaryDTO;
import com.leave_management_system.leave_management_system.dto.UpdateSecurityEventStatusDTO;
import com.leave_management_system.leave_management_system.entity.SecurityEventSeverity;
import com.leave_management_system.leave_management_system.entity.SecurityEventStatus;
import com.leave_management_system.leave_management_system.service.SecurityEventManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/security-events")
@Tag(name = "Security Events API", description = "Endpoints for monitoring security events, automated threat alerts, and resolution workflows")
@Validated
public class SecurityEventController {

    private final SecurityEventManagementService securityEventManagementService;

    public SecurityEventController(SecurityEventManagementService securityEventManagementService) {
        this.securityEventManagementService = securityEventManagementService;
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ADMIN_VIEW') or hasAuthority('AUDIT_LOG_VIEW') or hasAuthority('ROLE_HR')")
    @GetMapping
    @Operation(summary = "Search security events", description = "Query security events with multi-criteria filtering and pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved security events")
    })
    public Page<SecurityEventResponseDTO> searchSecurityEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) SecurityEventSeverity severity,
            @RequestParam(required = false) SecurityEventStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) Integer minThreatScore,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return securityEventManagementService.searchSecurityEvents(
                eventType, severity, status, userId, email, ipAddress, minThreatScore, startDate, endDate, pageable);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ADMIN_VIEW') or hasAuthority('AUDIT_LOG_VIEW') or hasAuthority('ROLE_HR')")
    @GetMapping("/summary")
    @Operation(summary = "Get security event posture summary", description = "Retrieves executive security metrics and incident resolution KPIs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved security posture summary")
    })
    public SecurityEventSummaryDTO getSummary() {
        return securityEventManagementService.getSummary();
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ADMIN_VIEW') or hasAuthority('AUDIT_LOG_VIEW') or hasAuthority('ROLE_HR')")
    @GetMapping("/{id}")
    @Operation(summary = "Get security event by ID", description = "Inspect a specific security incident record.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved security incident"),
            @ApiResponse(responseCode = "404", description = "Security event not found")
    })
    public SecurityEventResponseDTO getSecurityEventById(@PathVariable Long id) {
        return securityEventManagementService.getSecurityEventById(id);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE')")
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update security incident status", description = "Change incident status and document resolution/investigation notes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Security incident updated successfully"),
            @ApiResponse(responseCode = "404", description = "Security event not found")
    })
    public SecurityEventResponseDTO updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSecurityEventStatusDTO dto,
            Authentication authentication) {
        return securityEventManagementService.updateStatus(id, dto, authentication);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE')")
    @PostMapping("/{id}/investigate")
    @Operation(summary = "Mark incident under investigation", description = "Transition incident status to INVESTIGATING.")
    public SecurityEventResponseDTO investigate(
            @PathVariable Long id,
            @RequestParam(required = false) String notes,
            Authentication authentication) {
        UpdateSecurityEventStatusDTO dto = new UpdateSecurityEventStatusDTO(SecurityEventStatus.INVESTIGATING, notes);
        return securityEventManagementService.updateStatus(id, dto, authentication);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE')")
    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve security incident", description = "Transition incident status to RESOLVED with resolution notes.")
    public SecurityEventResponseDTO resolve(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Incident resolved and mitigated") String notes,
            Authentication authentication) {
        UpdateSecurityEventStatusDTO dto = new UpdateSecurityEventStatusDTO(SecurityEventStatus.RESOLVED, notes);
        return securityEventManagementService.updateStatus(id, dto, authentication);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE')")
    @PostMapping("/{id}/false-positive")
    @Operation(summary = "Mark incident as false positive", description = "Transition incident status to FALSE_POSITIVE with rationale.")
    public SecurityEventResponseDTO falsePositive(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Evaluated as benign / false positive") String notes,
            Authentication authentication) {
        UpdateSecurityEventStatusDTO dto = new UpdateSecurityEventStatusDTO(SecurityEventStatus.FALSE_POSITIVE, notes);
        return securityEventManagementService.updateStatus(id, dto, authentication);
    }
}
