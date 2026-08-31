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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
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
        employee.setActive(true);

        leaveType = new LeaveType("Annual Leave", "Desc", 20);
        leaveType.setId(1L);
        leaveType.setActive(true);

        leaveBalance = new LeaveBalance();
        leaveBalance.setId(1L);
        leaveBalance.setEmployee(employee);
        leaveBalance.setLeaveType(leaveType);
        leaveBalance.setAvailableDays(10);

        leaveRequest = new LeaveRequest();
        leaveRequest.setId(1L);
        leaveRequest.setEmployee(employee);
        leaveRequest.setLeaveType(leaveType);
        // "Use a fixed date (Thursday to Monday) to predictably test weekend exclusion (3 working days)."
        leaveRequest.setStartDate(LocalDate.of(2026, 9, 10)); // Thursday
        leaveRequest.setEndDate(LocalDate.of(2026, 9, 14)); // Monday
        leaveRequest.setReason("Vacation");
        leaveRequest.setStatus(LeaveStatus.PENDING);
    }

    @Test
    void createLeaveRequest_Success() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(false);
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveRequest created = leaveRequestService.createLeaveRequest(leaveRequest);

        assertNotNull(created);
        assertEquals(LeaveStatus.PENDING, created.getStatus());
    }

    @Test
    void createLeaveRequest_InactiveEmployee_ThrowsException() {
        employee.setActive(false);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.createLeaveRequest(leaveRequest));
        assertEquals("Inactive employees cannot apply for leave", ex.getMessage());
    }

    @Test
    void createLeaveRequest_InactiveLeaveType_ThrowsException() {
        leaveType.setActive(false);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.createLeaveRequest(leaveRequest));
        assertEquals("This leave type is not active", ex.getMessage());
    }

    @Test
    void createLeaveRequest_OverlappingLeave_ThrowsException() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.createLeaveRequest(leaveRequest));
        assertEquals("Leave request overlaps with an existing pending or approved leave", ex.getMessage());
    }

    @Test
    void createLeaveRequest_WeekendOnly_ThrowsException() {
        leaveRequest.setStartDate(LocalDate.of(2026, 9, 12)); // Saturday
        leaveRequest.setEndDate(LocalDate.of(2026, 9, 13)); // Sunday

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.createLeaveRequest(leaveRequest));
        assertEquals("Requested leave duration contains only weekends", ex.getMessage());
    }

    @Test
    void createLeaveRequest_InsufficientBalance_ThrowsException() {
        leaveRequest.setEndDate(LocalDate.of(2026, 9, 25)); // 12 working days requested, only 10 available

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(false);
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
        // "10 initial days - 3 requested working days (Thu-Mon) = 7 remaining days"
        assertEquals(7, leaveBalance.getAvailableDays());
        verify(leaveBalanceRepository, times(1)).save(leaveBalance);
    }

    @Test
    void rejectLeaveRequest_Success() {
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveRequest rejected = leaveRequestService.rejectLeaveRequest(1L, "Not enough coverage");

        assertEquals(LeaveStatus.REJECTED, rejected.getStatus());
        assertEquals("Not enough coverage", rejected.getRejectionReason());
        verify(leaveBalanceRepository, never()).save(any(LeaveBalance.class));
    }

    @Test
    void approveLeaveRequest_NotPending_ThrowsException() {
        leaveRequest.setStatus(LeaveStatus.APPROVED);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.approveLeaveRequest(1L));
        assertEquals("Only pending requests can be approved", ex.getMessage());
    }

    @Test
    void cancelLeaveRequest_Pending_Success() {
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveRequest cancelled = leaveRequestService.cancelLeaveRequest(1L);

        assertEquals(LeaveStatus.CANCELLED, cancelled.getStatus());
        verify(leaveBalanceRepository, never()).save(any(LeaveBalance.class));
    }

    @Test
    void cancelLeaveRequest_Approved_Success() {
        leaveRequest.setStatus(LeaveStatus.APPROVED);
        // "Balance should be initial 10, when cancelling an approved leave with 3 working days, it becomes 13."
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveRequest cancelled = leaveRequestService.cancelLeaveRequest(1L);

        assertEquals(LeaveStatus.CANCELLED, cancelled.getStatus());
        assertEquals(13, leaveBalance.getAvailableDays());
        verify(leaveBalanceRepository, times(1)).save(leaveBalance);
    }

    @Test
    void cancelLeaveRequest_Rejected_ThrowsException() {
        leaveRequest.setStatus(LeaveStatus.REJECTED);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.cancelLeaveRequest(1L));
        assertEquals("Cannot cancel a rejected leave request", ex.getMessage());
    }

    @Test
    void cancelLeaveRequest_AlreadyCancelled_ThrowsException() {
        leaveRequest.setStatus(LeaveStatus.CANCELLED);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.cancelLeaveRequest(1L));
        assertEquals("Leave request is already cancelled", ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchLeaveRequests_Success() {
        Page<LeaveRequest> page = new PageImpl<>(List.of(leaveRequest));
        when(leaveRequestRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<LeaveRequest> result = leaveRequestService.searchLeaveRequests(1L, LeaveStatus.PENDING, LocalDate.now(), LocalDate.now().plusDays(5), 1L, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }
}
