package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.entity.LeaveType;
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
    public ResponseEntity<LeaveType> createLeaveType(
            @Valid @RequestBody LeaveType leaveType) {

        return new ResponseEntity<>(
                leaveTypeService.createLeaveType(leaveType),
                HttpStatus.CREATED
        );
    }

    @GetMapping
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {

        return ResponseEntity.ok(
                leaveTypeService.getAllLeaveTypes()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveType> getLeaveTypeById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                leaveTypeService.getLeaveTypeById(id)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeaveType> updateLeaveType(
            @PathVariable Long id,
            @Valid @RequestBody LeaveType leaveType) {

        return ResponseEntity.ok(
                leaveTypeService.updateLeaveType(id, leaveType)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLeaveType(
            @PathVariable Long id) {

        leaveTypeService.deleteLeaveType(id);

        return ResponseEntity.noContent().build();
    }
}
