package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeResponseDTO;
import com.leave_management_system.leave_management_system.service.LeaveTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @PostMapping
    public ResponseEntity<LeaveTypeResponseDTO> createLeaveType(@Valid @RequestBody LeaveTypeRequestDTO dto) {
        return new ResponseEntity<>(leaveTypeService.createLeaveType(dto), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<LeaveTypeResponseDTO>> getAllLeaveTypes() {
        return ResponseEntity.ok(leaveTypeService.getAllLeaveTypes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveTypeResponseDTO> getLeaveTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.getLeaveTypeById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeaveTypeResponseDTO> updateLeaveType(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequestDTO dto) {
        return ResponseEntity.ok(leaveTypeService.updateLeaveType(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLeaveType(@PathVariable Long id) {
        leaveTypeService.deleteLeaveType(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<LeaveTypeResponseDTO> activateLeaveType(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.activateLeaveType(id));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<LeaveTypeResponseDTO> deactivateLeaveType(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.deactivateLeaveType(id));
    }
}
