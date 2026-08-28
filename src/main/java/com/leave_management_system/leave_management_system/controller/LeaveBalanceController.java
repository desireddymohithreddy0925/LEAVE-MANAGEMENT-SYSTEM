package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.service.LeaveBalanceService;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-balances")
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    public LeaveBalanceController(
            LeaveBalanceService leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @PostMapping
    public ResponseEntity<LeaveBalance> createLeaveBalance(

            @RequestParam Long employeeId,

            @RequestParam Long leaveTypeId,

            @RequestParam
            @Min(value = 0, message = "Available days cannot be negative")
            Integer availableDays) {

        return new ResponseEntity<>(
                leaveBalanceService.createLeaveBalance(
                        employeeId,
                        leaveTypeId,
                        availableDays
                ),
                HttpStatus.CREATED
        );
    }

    @GetMapping
    public ResponseEntity<List<LeaveBalance>> getAllLeaveBalances() {

        return ResponseEntity.ok(
                leaveBalanceService.getAllLeaveBalances()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveBalance> getLeaveBalanceById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                leaveBalanceService.getLeaveBalanceById(id)
        );
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<LeaveBalance>> getBalancesByEmployee(
            @PathVariable Long employeeId) {

        return ResponseEntity.ok(
                leaveBalanceService.getBalancesByEmployee(employeeId)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeaveBalance> updateLeaveBalance(
            @PathVariable Long id,
            @RequestParam
            @Min(value = 0, message = "Available days cannot be negative")
            Integer availableDays) {

        return ResponseEntity.ok(
                leaveBalanceService.updateLeaveBalance(
                        id,
                        availableDays
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLeaveBalance(
            @PathVariable Long id) {

        leaveBalanceService.deleteLeaveBalance(id);

        return ResponseEntity.noContent().build();
    }
}
