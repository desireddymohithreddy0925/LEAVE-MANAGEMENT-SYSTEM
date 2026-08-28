package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveBalanceRepository;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;

import java.util.List;

@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveBalanceService(
            LeaveBalanceRepository leaveBalanceRepository,
            EmployeeRepository employeeRepository,
            LeaveTypeRepository leaveTypeRepository) {

        this.leaveBalanceRepository = leaveBalanceRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    public LeaveBalance createLeaveBalance(
            Long employeeId,
            Long leaveTypeId,
            Integer availableDays) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with id: " + employeeId
                        ));

        LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Leave type not found with id: " + leaveTypeId
                        ));

        if (leaveBalanceRepository.existsByEmployeeAndLeaveType(
                employee, leaveType)) {

            throw new DuplicateResourceException(
                    "Leave balance already exists for this employee and leave type"
            );
        }

        LeaveBalance leaveBalance = new LeaveBalance();

        leaveBalance.setEmployee(employee);
        leaveBalance.setLeaveType(leaveType);
        leaveBalance.setAvailableDays(availableDays);

        return leaveBalanceRepository.save(leaveBalance);
    }

    public List<LeaveBalance> getAllLeaveBalances() {
        return leaveBalanceRepository.findAll();
    }

    public LeaveBalance getLeaveBalanceById(Long id) {

        return leaveBalanceRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Leave balance not found with id: " + id
                        ));
    }

    public List<LeaveBalance> getBalancesByEmployee(
            Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with id: " + employeeId
                        ));

        return leaveBalanceRepository.findByEmployee(employee);
    }

    public LeaveBalance updateLeaveBalance(
            Long id,
            Integer availableDays) {

        LeaveBalance leaveBalance = getLeaveBalanceById(id);

        leaveBalance.setAvailableDays(availableDays);

        return leaveBalanceRepository.save(leaveBalance);
    }

    public void deleteLeaveBalance(Long id) {

        LeaveBalance leaveBalance = getLeaveBalanceById(id);

        leaveBalanceRepository.delete(leaveBalance);
    }
}
