package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.LeaveBalanceRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.exception.InsufficientLeaveException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
                               LeaveBalanceRepository leaveBalanceRepository,
                               EmployeeRepository employeeRepository,
                               LeaveTypeRepository leaveTypeRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    public LeaveRequest createLeaveRequest(LeaveRequest leaveRequest) {
        if (leaveRequest.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Start date cannot be in the past");
        }

        if (leaveRequest.getEndDate().isBefore(leaveRequest.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        Employee employee = employeeRepository.findById(leaveRequest.getEmployee().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        // "Check if employee is active. Inactive employees cannot apply for leave."
        if (!employee.isActive()) {
            throw new IllegalArgumentException("Inactive employees cannot apply for leave");
        }

        LeaveType leaveType = leaveTypeRepository.findById(leaveRequest.getLeaveType().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        // "Check if leave type is active."
        if (!leaveType.isActive()) {
            throw new IllegalArgumentException("This leave type is not active");
        }

        // "Overlap detection: check if there's any pending or approved leave for these dates."
        boolean hasOverlap = leaveRequestRepository.hasOverlappingLeave(
                employee,
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
        );
        if (hasOverlap) {
            throw new IllegalArgumentException("Leave request overlaps with an existing pending or approved leave");
        }

        long requestedDays = calculateWorkingDays(leaveRequest.getStartDate(), leaveRequest.getEndDate());
        // "Ensure the requested duration contains at least one working day."
        if (requestedDays == 0) {
            throw new IllegalArgumentException("Requested leave duration contains only weekends");
        }

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeAndLeaveType(employee, leaveType)
                .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found for this employee and leave type"));

        if (balance.getAvailableDays() < requestedDays) {
            throw new InsufficientLeaveException("Insufficient leave balance. Available: " + balance.getAvailableDays() + ", Requested: " + requestedDays);
        }

        leaveRequest.setEmployee(employee);
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setStatus(LeaveStatus.PENDING);

        return leaveRequestRepository.save(leaveRequest);
    }

    public List<LeaveRequest> getAllLeaveRequests() {
        return leaveRequestRepository.findAll();
    }

    public LeaveRequest getLeaveRequestById(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
    }

    public List<LeaveRequest> getLeaveRequestsByEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        return leaveRequestRepository.findByEmployee(employee);
    }

    public List<LeaveRequest> getLeaveRequestsByStatus(LeaveStatus status) {
        return leaveRequestRepository.findByStatus(status);
    }

    public LeaveRequest approveLeaveRequest(Long id) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be approved");
        }

        long requestedDays = calculateWorkingDays(leaveRequest.getStartDate(), leaveRequest.getEndDate());

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeAndLeaveType(leaveRequest.getEmployee(), leaveRequest.getLeaveType())
                .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found"));

        if (balance.getAvailableDays() < requestedDays) {
            throw new InsufficientLeaveException("Insufficient leave balance");
        }

        balance.setAvailableDays(balance.getAvailableDays() - (int) requestedDays);
        leaveBalanceRepository.save(balance);

        leaveRequest.setStatus(LeaveStatus.APPROVED);
        return leaveRequestRepository.save(leaveRequest);
    }

    public LeaveRequest rejectLeaveRequest(Long id) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be rejected");
        }

        leaveRequest.setStatus(LeaveStatus.REJECTED);
        return leaveRequestRepository.save(leaveRequest);
    }

    public void deleteLeaveRequest(Long id) {
        LeaveRequest existing = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        leaveRequestRepository.delete(existing);
    }

    // "Helper method to calculate working days between two dates, excluding weekends (Saturdays and Sundays)."
    private long calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        long workingDays = 0;
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            java.time.DayOfWeek day = date.getDayOfWeek();
            if (day != java.time.DayOfWeek.SATURDAY && day != java.time.DayOfWeek.SUNDAY) {
                workingDays++;
            }
            date = date.plusDays(1);
        }
        return workingDays;
    }
}