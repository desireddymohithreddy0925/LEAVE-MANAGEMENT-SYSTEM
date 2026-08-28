package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.exception.InsufficientLeaveException;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveBalanceRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @InjectMocks
    private LeaveRequestService leaveRequestService;

    private Employee employee;
    private LeaveType leaveType;
    private LeaveBalance leaveBalance;
    private LeaveRequest leaveRequest;

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
        leaveBalance.setAvailableDays(10);

        leaveRequest = new LeaveRequest();
        leaveRequest.setId(1L);
        leaveRequest.setEmployee(employee);
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setStartDate(LocalDate.now().plusDays(1));
        leaveRequest.setEndDate(LocalDate.now().plusDays(5)); // 5 days
        leaveRequest.setReason("Vacation");
        leaveRequest.setStatus(LeaveStatus.PENDING);
    }

    @Test
    void createLeaveRequest_Success() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveRequest created = leaveRequestService.createLeaveRequest(leaveRequest);

        assertNotNull(created);
        assertEquals(LeaveStatus.PENDING, created.getStatus());
    }

    @Test
    void createLeaveRequest_InsufficientBalance_ThrowsException() {
        leaveRequest.setEndDate(LocalDate.now().plusDays(15)); // 15 days requested, only 10 available

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));

        assertThrows(InsufficientLeaveException.class, () -> leaveRequestService.createLeaveRequest(leaveRequest));
    }

    @Test
    void approveLeaveRequest_Success() {
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveRequest approved = leaveRequestService.approveLeaveRequest(1L);

        assertEquals(LeaveStatus.APPROVED, approved.getStatus());
        assertEquals(5, leaveBalance.getAvailableDays()); // 10 - 5 = 5
        verify(leaveBalanceRepository, times(1)).save(leaveBalance);
    }

    @Test
    void rejectLeaveRequest_Success() {
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveRequest rejected = leaveRequestService.rejectLeaveRequest(1L);

        assertEquals(LeaveStatus.REJECTED, rejected.getStatus());
        verify(leaveBalanceRepository, never()).save(any(LeaveBalance.class));
    }
}
