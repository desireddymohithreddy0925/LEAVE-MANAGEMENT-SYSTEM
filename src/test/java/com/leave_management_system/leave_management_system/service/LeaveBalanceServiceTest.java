package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveBalanceRepository;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeaveBalanceServiceTest {

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @InjectMocks
    private LeaveBalanceService leaveBalanceService;

    private Employee employee;
    private LeaveType leaveType;
    private LeaveBalance leaveBalance;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);

        leaveType = new LeaveType("Annual Leave", "Desc", 20);
        leaveType.setId(1L);

        leaveBalance = new LeaveBalance();
        leaveBalance.setId(1L);
        leaveBalance.setEmployee(employee);
        leaveBalance.setLeaveType(leaveType);
        leaveBalance.setAvailableDays(20);
    }

    @Test
    void createLeaveBalance_Success() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveType(employee, leaveType)).thenReturn(false);
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenReturn(leaveBalance);

        LeaveBalance created = leaveBalanceService.createLeaveBalance(1L, 1L, 20);

        assertNotNull(created);
        assertEquals(20, created.getAvailableDays());
    }

    @Test
    void createLeaveBalance_Duplicate_ThrowsException() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveType(employee, leaveType)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> leaveBalanceService.createLeaveBalance(1L, 1L, 20));
    }
}
