package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.service.LeaveRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.*;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @PostMapping
    public LeaveResponseDTO createLeaveRequest(@Valid @RequestBody LeaveRequestDTO dto) {
        return leaveRequestService.createLeaveRequest(dto);
    }

    @GetMapping
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
    public LeaveResponseDTO getLeaveRequestById(@PathVariable Long id) {
        return leaveRequestService.getLeaveRequestById(id);
    }

    @GetMapping("/employee/{employeeId}")
    public List<LeaveResponseDTO> getLeaveRequestsByEmployee(@PathVariable Long employeeId) {
        return leaveRequestService.getLeaveRequestsByEmployee(employeeId);
    }

    @GetMapping("/status/{status}")
    public List<LeaveResponseDTO> getLeaveRequestsByStatus(@PathVariable LeaveStatus status) {
        return leaveRequestService.getLeaveRequestsByStatus(status);
    }

    @PutMapping("/{id}/approve")
    public LeaveResponseDTO approveLeaveRequest(@PathVariable Long id) {
        return leaveRequestService.approveLeaveRequest(id);
    }

    @PutMapping("/{id}/reject")
    public LeaveResponseDTO rejectLeaveRequest(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return leaveRequestService.rejectLeaveRequest(id, reason);
    }

    @PutMapping("/{id}/cancel")
    public LeaveResponseDTO cancelLeaveRequest(@PathVariable Long id) {
        return leaveRequestService.cancelLeaveRequest(id);
    }
}