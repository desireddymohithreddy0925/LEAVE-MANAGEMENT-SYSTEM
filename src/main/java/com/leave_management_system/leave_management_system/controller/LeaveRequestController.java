package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.dto.OverlapCheckResponseDTO;
import com.leave_management_system.leave_management_system.service.LeaveRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.*;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.Authentication;
import com.leave_management_system.leave_management_system.security.SecurityAuthorizationService;

@RestController
@RequestMapping("/api/leave-requests")
@Validated
@Tag(name = "Leave Request API", description = "Endpoints for managing employee leave requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final SecurityAuthorizationService securityService;

    public LeaveRequestController(LeaveRequestService leaveRequestService,
            SecurityAuthorizationService securityService) {
        this.leaveRequestService = leaveRequestService;
        this.securityService = securityService;
    }

    @PreAuthorize("hasAuthority('LEAVE_CREATE')")
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "Apply for leave", description = "Creates a new leave request for an employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Leave request created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data or business rule violation (e.g. overlapping leaves)"),
            @ApiResponse(responseCode = "404", description = "Employee or Leave type not found")
    })
    public LeaveResponseDTO createLeaveRequest(@Valid @RequestBody LeaveRequestDTO dto) {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();

        boolean isHRorAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));

        if (!isHRorAdmin) {
            // Force employeeId to be the authenticated user's ID
            com.leave_management_system.leave_management_system.entity.Employee employee = securityService
                    .getAuthenticatedEmployee(authentication)
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
            dto.setEmployeeId(employee.getId());
        } else if (dto.getEmployeeId() == null) {
            securityService.getAuthenticatedEmployee(authentication)
                    .ifPresentOrElse(
                            emp -> dto.setEmployeeId(emp.getId()),
                            () -> {
                                throw new IllegalArgumentException(
                                        "Employee ID is required when created by HR or ADMIN");
                            });
        }

        return leaveRequestService.createLeaveRequest(dto);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/check-overlap")
    @Operation(summary = "Check for overlapping leaves", description = "Checks if prospective leave dates conflict with existing pending or approved leaves.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully evaluated overlap status"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "403", description = "Forbidden from checking overlap for target employee"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public OverlapCheckResponseDTO checkOverlap(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long employeeId) {

        boolean isElevated = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_MANAGER"));

        Long targetEmployeeId;
        if (!isElevated || employeeId == null) {
            com.leave_management_system.leave_management_system.entity.Employee employee = securityService
                    .getAuthenticatedEmployee(authentication)
                    .orElseThrow(() -> new com.leave_management_system.leave_management_system.exception.ResourceNotFoundException(
                            "Authenticated employee not found"));
            targetEmployeeId = (isElevated && employeeId != null) ? employeeId : employee.getId();
        } else {
            targetEmployeeId = employeeId;
        }

        return leaveRequestService.checkOverlap(targetEmployeeId, startDate, endDate);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    @Operation(summary = "Get current employee leave requests", description = "Returns a paginated list of leave requests for the currently authenticated employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved leave requests"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Employee record not found")
    })
    public Page<LeaveResponseDTO> getMyLeaveRequests(
            Authentication authentication,
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) Integer year,
            Pageable pageable) {

        com.leave_management_system.leave_management_system.entity.Employee employee = securityService
                .getAuthenticatedEmployee(authentication)
                .orElseThrow(
                        () -> new com.leave_management_system.leave_management_system.exception.ResourceNotFoundException(
                                "Authenticated employee not found"));

        return leaveRequestService.getMyLeaveRequests(employee.getId(), status, year, pageable);
    }

    @PreAuthorize("hasAuthority('LEAVE_VIEW_ALL')")
    @GetMapping
    @Operation(summary = "Search leave requests", description = "Search leave requests with filtering and pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    })
    public Page<LeaveResponseDTO> getLeaveRequests(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) Long departmentId,
            Pageable pageable) {

        return leaveRequestService.searchLeaveRequests(employeeId, status, startDate, endDate, leaveTypeId,
                departmentId, pageable);
    }

    @PreAuthorize("@securityService.canViewLeaveRequest(authentication, #id)")
    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Get leave request by ID", description = "Returns the details of a specific leave request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved leave request"),
            @ApiResponse(responseCode = "404", description = "Leave request not found")
    })
    public LeaveResponseDTO getLeaveRequestById(@PathVariable Long id) {
        return leaveRequestService.getLeaveRequestById(id);
    }

    @PreAuthorize("@securityService.canViewEmployee(authentication, #employeeId)")
    @GetMapping("/employee/{employeeId:\\d+}")
    @Operation(summary = "Get employee leave requests", description = "Returns all leave requests for a specific employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved leave requests"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public List<LeaveResponseDTO> getLeaveRequestsByEmployee(@PathVariable Long employeeId) {
        return leaveRequestService.getLeaveRequestsByEmployee(employeeId);
    }

    @PreAuthorize("hasAuthority('LEAVE_VIEW_ALL')")
    @GetMapping("/status/{status}")
    @Operation(summary = "Get leave requests by status", description = "Returns all leave requests with a specific status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved leave requests")
    })
    public List<LeaveResponseDTO> getLeaveRequestsByStatus(@PathVariable LeaveStatus status) {
        return leaveRequestService.getLeaveRequestsByStatus(status);
    }

    @PreAuthorize("@securityService.canApproveLeaveRequest(authentication, #id)")
    @RequestMapping(value = "/{id:\\d+}/approve", method = { RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.POST })
    @Operation(summary = "Approve a leave request", description = "Approves a pending leave request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave request approved successfully"),
            @ApiResponse(responseCode = "400", description = "Request is not in pending status or insufficient balance"),
            @ApiResponse(responseCode = "403", description = "Forbidden from approving leave request"),
            @ApiResponse(responseCode = "404", description = "Leave request not found")
    })
    public LeaveResponseDTO approveLeaveRequest(@PathVariable Long id) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return leaveRequestService.approveLeaveRequest(id);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException |
                     jakarta.persistence.OptimisticLockException |
                     org.springframework.dao.PessimisticLockingFailureException |
                     jakarta.persistence.PessimisticLockException ex) {
                if (attempt == maxAttempts) {
                    throw ex;
                }
                try {
                    Thread.sleep(40L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        return leaveRequestService.approveLeaveRequest(id);
    }

    @PreAuthorize("@securityService.canRejectLeaveRequest(authentication, #id)")
    @RequestMapping(value = "/{id:\\d+}/reject", method = { RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.POST })
    @Operation(summary = "Reject a leave request", description = "Rejects a pending leave request with a mandatory reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave request rejected successfully"),
            @ApiResponse(responseCode = "400", description = "Request is not in pending status or reason is missing/invalid"),
            @ApiResponse(responseCode = "403", description = "Forbidden from rejecting leave request"),
            @ApiResponse(responseCode = "404", description = "Leave request not found")
    })
    public LeaveResponseDTO rejectLeaveRequest(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) @jakarta.validation.Valid com.leave_management_system.leave_management_system.dto.RejectRequestDTO body) {
        String effectiveReason = (reason != null && !reason.trim().isEmpty())
                ? reason.trim()
                : (body != null ? body.getReason() : null);
        return leaveRequestService.rejectLeaveRequest(id, effectiveReason);
    }

    @PreAuthorize("@securityService.canCancelLeaveRequest(authentication, #id)")
    @RequestMapping(value = "/{id:\\d+}/cancel", method = { RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.POST })
    @Operation(summary = "Cancel a leave request", description = "Cancels a pending or approved leave request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave request cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Request is already cancelled or rejected"),
            @ApiResponse(responseCode = "403", description = "Forbidden from cancelling leave request"),
            @ApiResponse(responseCode = "404", description = "Leave request not found")
    })
    public LeaveResponseDTO cancelLeaveRequest(@PathVariable Long id) {
        return leaveRequestService.cancelLeaveRequest(id);
    }

    @PreAuthorize("@securityService.canViewLeaveRequest(authentication, #id)")
    @GetMapping("/{id:\\d+}/signoffs")
    @Operation(summary = "Get leave request signoffs", description = "Returns the list of concurrent approval signoffs for a leave request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved signoffs"),
            @ApiResponse(responseCode = "404", description = "Leave request not found")
    })
    public java.util.List<com.leave_management_system.leave_management_system.dto.LeaveSignoffResponseDTO> getLeaveRequestSignoffs(@PathVariable Long id) {
        return leaveRequestService.getLeaveRequestSignoffs(id);
    }
}