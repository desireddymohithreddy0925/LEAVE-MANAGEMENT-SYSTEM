package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceResponseDTO;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import java.util.stream.Collectors;
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

    public LeaveBalanceResponseDTO createLeaveBalance(LeaveBalanceRequestDTO dto) {

        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with id: " + dto.getEmployeeId()
                        ));

        LeaveType leaveType = leaveTypeRepository.findById(dto.getLeaveTypeId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Leave type not found with id: " + dto.getLeaveTypeId()
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
        leaveBalance.setAvailableDays(dto.getAvailableDays());

        return LeaveBalanceResponseDTO.fromEntity(leaveBalanceRepository.save(leaveBalance));
    }

    public List<LeaveBalanceResponseDTO> getAllLeaveBalances() {
        return leaveBalanceRepository.findAll().stream()
                .map(LeaveBalanceResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public LeaveBalanceResponseDTO getLeaveBalanceById(Long id) {

        LeaveBalance balance = leaveBalanceRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Leave balance not found with id: " + id
                        ));
        return LeaveBalanceResponseDTO.fromEntity(balance);
    }

    public LeaveBalance getLeaveBalanceEntityById(Long id) {
        return leaveBalanceRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Leave balance not found with id: " + id
                        ));
    }

    public List<LeaveBalanceResponseDTO> getBalancesByEmployee(
            Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with id: " + employeeId
                        ));

        return leaveBalanceRepository.findByEmployee(employee).stream()
                .map(LeaveBalanceResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public LeaveBalanceResponseDTO updateLeaveBalance(
            Long id,
            LeaveBalanceRequestDTO dto) {

        LeaveBalance leaveBalance = getLeaveBalanceEntityById(id);

        // In an update, typically we only update the available days, 
        // assuming employeeId and leaveTypeId cannot be changed.
        // If they need to be changed, additional validation is required.
        leaveBalance.setAvailableDays(dto.getAvailableDays());

        return LeaveBalanceResponseDTO.fromEntity(leaveBalanceRepository.save(leaveBalance));
    }

    public void deleteLeaveBalance(Long id) {

        LeaveBalance leaveBalance = getLeaveBalanceEntityById(id);

        leaveBalanceRepository.delete(leaveBalance);
    }
}
