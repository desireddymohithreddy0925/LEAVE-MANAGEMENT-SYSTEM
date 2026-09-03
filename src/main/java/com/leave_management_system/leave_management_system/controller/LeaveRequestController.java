package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.service.LeaveRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

@RestController
@RequestMapping("/api/leave-requests")
@Validated
@Tag(name = "Leave Request API", description = "Endpoints for managing employee leave requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "Apply for leave", description = "Creates a new leave request for an employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Leave request created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data or business rule violation (e.g. overlapping leaves)"),
            @ApiResponse(responseCode = "404", description = "Employee or Leave type not found")
    })
    public LeaveResponseDTO createLeaveRequest(@Valid @RequestBody LeaveRequestDTO dto) {
        return leaveRequestService.createLeaveRequest(dto);
    }

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
            Pageable pageable) {
        
        return leaveRequestService.searchLeaveRequests(employeeId, status, startDate, endDate, leaveTypeId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get leave request by ID", description = "Returns the details of a specific leave request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved leave request"),
            @ApiResponse(responseCode = "404", description = "Leave request not found")
    })
    public LeaveResponseDTO getLeaveRequestById(@PathVariable Long id) {
        return leaveRequestService.getLeaveRequestById(id);
    }

    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get employee leave requests", description = "Returns all leave requests for a specific employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved leave requests"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public List<LeaveResponseDTO> getLeaveRequestsByEmployee(@PathVariable Long employeeId) {
        return leaveRequestService.getLeaveRequestsByEmployee(employeeId);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get leave requests by status", description = "Returns all leave requests with a specific status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved leave requests")
    })
    public List<LeaveResponseDTO> getLeaveRequestsByStatus(@PathVariable LeaveStatus status) {
        return leaveRequestService.getLeaveRequestsByStatus(status);
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve a leave request", description = "Approves a pending leave request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave request approved successfully"),
            @ApiResponse(responseCode = "400", description = "Request is not in pending status or insufficient balance"),
            @ApiResponse(responseCode = "404", description = "Leave request not found")
    })
    public LeaveResponseDTO approveLeaveRequest(@PathVariable Long id) {
        return leaveRequestService.approveLeaveRequest(id);
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject a leave request", description = "Rejects a pending leave request with a mandatory reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave request rejected successfully"),
            @ApiResponse(responseCode = "400", description = "Request is not in pending status or reason is missing"),
            @ApiResponse(responseCode = "404", description = "Leave request not found")
    })
    public LeaveResponseDTO rejectLeaveRequest(@PathVariable Long id, @RequestParam(required = true) @NotBlank(message = "Rejection reason is mandatory") @Size(max = 500, message = "Rejection reason must not exceed 500 characters") String reason) {
        return leaveRequestService.rejectLeaveRequest(id, reason);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel a leave request", description = "Cancels a pending or approved leave request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave request cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Request is already cancelled or rejected"),
            @ApiResponse(responseCode = "404", description = "Leave request not found")
    })
    public LeaveResponseDTO cancelLeaveRequest(@PathVariable Long id) {
        return leaveRequestService.cancelLeaveRequest(id);
    }
}