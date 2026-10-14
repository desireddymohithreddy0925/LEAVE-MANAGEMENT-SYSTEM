package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceResponseDTO;
import com.leave_management_system.leave_management_system.service.LeaveBalanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.security.SecurityAuthorizationService;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/leave-balances")
@Tag(name = "Leave Balance API", description = "Endpoints for managing employee leave balances")
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;
    private final SecurityAuthorizationService securityService;

    public LeaveBalanceController(
            LeaveBalanceService leaveBalanceService,
            SecurityAuthorizationService securityService) {
        this.leaveBalanceService = leaveBalanceService;
        this.securityService = securityService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    @Operation(summary = "Get current employee leave balances", description = "Returns all leave balances for the currently authenticated employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved balances"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Employee profile not found")
    })
    public ResponseEntity<List<LeaveBalanceResponseDTO>> getMyBalances(
            Authentication authentication,
            @RequestParam(required = false) Integer year) {

        Employee employee = securityService.getAuthenticatedEmployee(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated employee not found"));

        return ResponseEntity.ok(
                leaveBalanceService.getBalancesByEmployee(employee.getId(), year)
        );
    }

    @PreAuthorize("hasAuthority('LEAVE_BALANCE_MANAGE')")
    @PostMapping
    @Operation(summary = "Create or add leave balance", description = "Adds a leave balance for an employee. Initial total allocation can be explicitly set or defaulted to the leave type default days. If available days are supplied, they must match total days.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Leave balance created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Employee or Leave type not found"),
            @ApiResponse(responseCode = "409", description = "Leave balance already exists")
    })
    public ResponseEntity<LeaveBalanceResponseDTO> createLeaveBalance(
            @Valid @RequestBody LeaveBalanceRequestDTO dto) {

        return new ResponseEntity<>(
                leaveBalanceService.createLeaveBalance(dto),
                HttpStatus.CREATED
        );
    }

    @PreAuthorize("hasAuthority('LEAVE_BALANCE_MANAGE')")
    @GetMapping
    @Operation(summary = "Get all leave balances", description = "Returns a list of all leave balances across all employees.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    })
    public ResponseEntity<List<LeaveBalanceResponseDTO>> getAllLeaveBalances() {

        return ResponseEntity.ok(
                leaveBalanceService.getAllLeaveBalances()
        );
    }

    @PreAuthorize("hasAuthority('LEAVE_BALANCE_MANAGE')")
    @GetMapping("/{id}")
    @Operation(summary = "Get leave balance by ID", description = "Returns the details of a specific leave balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved balance"),
            @ApiResponse(responseCode = "404", description = "Leave balance not found")
    })
    public ResponseEntity<LeaveBalanceResponseDTO> getLeaveBalanceById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                leaveBalanceService.getLeaveBalanceById(id)
        );
    }

    @PreAuthorize("@securityService.canViewEmployee(authentication, #employeeId)")
    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get employee balances", description = "Returns all leave balances for a specific employee, optionally filtered by year.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved balances"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public ResponseEntity<List<LeaveBalanceResponseDTO>> getBalancesByEmployee(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {

        return ResponseEntity.ok(
                leaveBalanceService.getBalancesByEmployee(employeeId, year)
        );
    }

    @PreAuthorize("hasAuthority('LEAVE_BALANCE_MANAGE')")
    @PostMapping("/employee/{employeeId}/initialize")
    @Operation(summary = "Initialize balances for employee", description = "Initializes default leave balances for all active leave types for an employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave balances initialized successfully"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public ResponseEntity<List<LeaveBalanceResponseDTO>> initializeBalances(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {

        return ResponseEntity.ok(
                leaveBalanceService.initializeBalancesForEmployee(employeeId, year)
        );
    }

    @PreAuthorize("hasAuthority('LEAVE_BALANCE_MANAGE')")
    @PutMapping("/{id}")
    @Operation(summary = "Update a leave balance", description = "Updates available and/or total days for a leave balance. Validates that used + available days does not exceed total days.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave balance updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Leave balance not found")
    })
    public ResponseEntity<LeaveBalanceResponseDTO> updateLeaveBalance(
            @PathVariable Long id,
            @Valid @RequestBody LeaveBalanceRequestDTO dto) {

        return ResponseEntity.ok(
                leaveBalanceService.updateLeaveBalance(id, dto)
        );
    }

    @PreAuthorize("hasAuthority('LEAVE_BALANCE_MANAGE')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a leave balance", description = "Deletes a leave balance record.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Leave balance deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Leave balance not found")
    })
    public ResponseEntity<Void> deleteLeaveBalance(
            @PathVariable Long id) {

        leaveBalanceService.deleteLeaveBalance(id);

        return ResponseEntity.noContent().build();
    }
}
