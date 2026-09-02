package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeResponseDTO;
import com.leave_management_system.leave_management_system.service.LeaveTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-types")
@Tag(name = "Leave Type API", description = "Endpoints for managing leave types")
public class LeaveTypeController {

    // "Service handles the business logic for leave types."
    private final LeaveTypeService leaveTypeService;

    // "Constructor injection for LeaveTypeService."
    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @PostMapping
    @Operation(summary = "Create a new leave type", description = "Creates a new type of leave (e.g., Annual, Sick).")
    public ResponseEntity<LeaveTypeResponseDTO> createLeaveType(@Valid @RequestBody LeaveTypeRequestDTO dto) {
        return new ResponseEntity<>(leaveTypeService.createLeaveType(dto), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Get all leave types", description = "Returns a list of all available leave types.")
    public ResponseEntity<List<LeaveTypeResponseDTO>> getAllLeaveTypes() {
        return ResponseEntity.ok(leaveTypeService.getAllLeaveTypes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get leave type by ID", description = "Returns the details of a specific leave type.")
    public ResponseEntity<LeaveTypeResponseDTO> getLeaveTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.getLeaveTypeById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a leave type", description = "Updates the details of an existing leave type.")
    public ResponseEntity<LeaveTypeResponseDTO> updateLeaveType(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequestDTO dto) {
        return ResponseEntity.ok(leaveTypeService.updateLeaveType(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a leave type", description = "Deletes a leave type from the system.")
    public ResponseEntity<Void> deleteLeaveType(@PathVariable Long id) {
        leaveTypeService.deleteLeaveType(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate a leave type", description = "Marks a leave type as active, allowing employees to apply for it.")
    public ResponseEntity<LeaveTypeResponseDTO> activateLeaveType(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.activateLeaveType(id));
    }

    @PutMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a leave type", description = "Marks a leave type as inactive.")
    public ResponseEntity<LeaveTypeResponseDTO> deactivateLeaveType(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.deactivateLeaveType(id));
    }
}
