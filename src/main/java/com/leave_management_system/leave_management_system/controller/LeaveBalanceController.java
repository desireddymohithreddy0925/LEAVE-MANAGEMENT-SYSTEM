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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-balances")
@Tag(name = "Leave Balance API", description = "Endpoints for managing employee leave balances")
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    public LeaveBalanceController(
            LeaveBalanceService leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @PostMapping
    @Operation(summary = "Create or add leave balance", description = "Adds a leave balance for an employee.")
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

    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get employee balances", description = "Returns all leave balances for a specific employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved balances"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public ResponseEntity<List<LeaveBalanceResponseDTO>> getBalancesByEmployee(
            @PathVariable Long employeeId) {

        return ResponseEntity.ok(
                leaveBalanceService.getBalancesByEmployee(employeeId)
        );
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a leave balance", description = "Updates the available days for a leave balance.")
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
