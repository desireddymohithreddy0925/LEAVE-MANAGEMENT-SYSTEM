package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeResponseDTO;
import com.leave_management_system.leave_management_system.service.LeaveTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-types")
@Tag(name = "Leave Type API", description = "Endpoints for managing leave types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Operation(summary = "Create a new leave type", description = "Creates a new type of leave (e.g., Annual, Sick).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Leave type created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Leave type name already exists")
    })
    public ResponseEntity<LeaveTypeResponseDTO> createLeaveType(@Valid @RequestBody LeaveTypeRequestDTO dto) {
        return new ResponseEntity<>(leaveTypeService.createLeaveType(dto), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all leave types", description = "Returns a list of all available leave types.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    })
    public ResponseEntity<List<LeaveTypeResponseDTO>> getAllLeaveTypes() {
        return ResponseEntity.ok(leaveTypeService.getAllLeaveTypes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get leave type by ID", description = "Returns the details of a specific leave type.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved leave type"),
            @ApiResponse(responseCode = "404", description = "Leave type not found")
    })
    public ResponseEntity<LeaveTypeResponseDTO> getLeaveTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.getLeaveTypeById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    @Operation(summary = "Update a leave type", description = "Updates the details of an existing leave type.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave type updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Leave type not found"),
            @ApiResponse(responseCode = "409", description = "Leave type name already exists")
    })
    public ResponseEntity<LeaveTypeResponseDTO> updateLeaveType(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequestDTO dto) {
        return ResponseEntity.ok(leaveTypeService.updateLeaveType(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a leave type", description = "Deletes a leave type from the system.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Leave type deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot delete leave type with existing balances or requests"),
            @ApiResponse(responseCode = "404", description = "Leave type not found")
    })
    public ResponseEntity<Void> deleteLeaveType(@PathVariable Long id) {
        leaveTypeService.deleteLeaveType(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate a leave type", description = "Marks a leave type as active, allowing employees to apply for it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave type activated successfully"),
            @ApiResponse(responseCode = "404", description = "Leave type not found")
    })
    public ResponseEntity<LeaveTypeResponseDTO> activateLeaveType(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.activateLeaveType(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a leave type", description = "Marks a leave type as inactive.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leave type deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Leave type not found")
    })
    public ResponseEntity<LeaveTypeResponseDTO> deactivateLeaveType(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.deactivateLeaveType(id));
    }
}
