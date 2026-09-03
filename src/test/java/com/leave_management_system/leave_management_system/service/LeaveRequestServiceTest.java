package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
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
    private LeaveRequestDTO requestDTO;

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
        leaveRequest.setStartDate(LocalDate.of(2026, 9, 10));
        leaveRequest.setEndDate(LocalDate.of(2026, 9, 14));
        leaveRequest.setReason("Vacation");
        leaveRequest.setStatus(LeaveStatus.PENDING);

        requestDTO = new LeaveRequestDTO();
        requestDTO.setEmployeeId(1L);
        requestDTO.setLeaveTypeId(1L);
        requestDTO.setStartDate(LocalDate.of(2026, 9, 10));
        requestDTO.setEndDate(LocalDate.of(2026, 9, 14));
        requestDTO.setReason("Vacation");
    }

    @Test
    void createLeaveRequest_Success() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(false);
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveResponseDTO created = leaveRequestService.createLeaveRequest(requestDTO);

        assertNotNull(created);
        assertEquals(LeaveStatus.PENDING, created.getStatus());
    }



    @Test
    void createLeaveRequest_EndDateBeforeStartDate_ThrowsException() {
        requestDTO.setEndDate(LocalDate.of(2026, 9, 9)); // Before start date of 2026-09-10

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.createLeaveRequest(requestDTO));
        assertEquals("End date cannot be before start date", ex.getMessage());
    }

    @Test
    void createLeaveRequest_InactiveEmployee_ThrowsException() {
        employee.setActive(false);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.createLeaveRequest(requestDTO));
        assertEquals("Inactive employees cannot apply for leave", ex.getMessage());
    }

    @Test
    void createLeaveRequest_InactiveLeaveType_ThrowsException() {
        leaveType.setActive(false);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.createLeaveRequest(requestDTO));
        assertEquals("This leave type is not active", ex.getMessage());
    }

    @Test
    void createLeaveRequest_OverlappingLeave_ThrowsException() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.createLeaveRequest(requestDTO));
        assertEquals("Leave request overlaps with an existing pending or approved leave", ex.getMessage());
    }

    @Test
    void createLeaveRequest_WeekendOnly_ThrowsException() {
        requestDTO.setStartDate(LocalDate.of(2026, 9, 12));
        requestDTO.setEndDate(LocalDate.of(2026, 9, 13));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.createLeaveRequest(requestDTO));
        assertEquals("Requested leave duration contains only weekends", ex.getMessage());
    }

    @Test
    void createLeaveRequest_InsufficientBalance_ThrowsException() {
        requestDTO.setEndDate(LocalDate.of(2026, 9, 25));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(false);
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));

        assertThrows(InsufficientLeaveException.class, () -> leaveRequestService.createLeaveRequest(requestDTO));
    }

    @Test
    void approveLeaveRequest_Success() {
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveResponseDTO approved = leaveRequestService.approveLeaveRequest(1L);

        assertEquals(LeaveStatus.APPROVED, approved.getStatus());
        assertEquals(7, leaveBalance.getAvailableDays());
        verify(leaveBalanceRepository, times(1)).save(leaveBalance);
    }

    @Test
    void rejectLeaveRequest_Success() {
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveResponseDTO rejected = leaveRequestService.rejectLeaveRequest(1L, "Not enough coverage");

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

        LeaveResponseDTO cancelled = leaveRequestService.cancelLeaveRequest(1L);

        assertEquals(LeaveStatus.CANCELLED, cancelled.getStatus());
        verify(leaveBalanceRepository, never()).save(any(LeaveBalance.class));
    }

    @Test
    void cancelLeaveRequest_Approved_Success() {
        leaveRequest.setStatus(LeaveStatus.APPROVED);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenReturn(leaveRequest);

        LeaveResponseDTO cancelled = leaveRequestService.cancelLeaveRequest(1L);

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

        Page<LeaveResponseDTO> result = leaveRequestService.searchLeaveRequests(1L, LeaveStatus.PENDING, LocalDate.now(), LocalDate.now().plusDays(5), 1L, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }



    @Test
    void approveLeaveRequest_Rejected_ThrowsException() {
        leaveRequest.setStatus(LeaveStatus.REJECTED);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.approveLeaveRequest(1L));
        assertEquals("Only pending requests can be approved", ex.getMessage());
    }
    
    @Test
    void approveLeaveRequest_Cancelled_ThrowsException() {
        leaveRequest.setStatus(LeaveStatus.CANCELLED);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.approveLeaveRequest(1L));
        assertEquals("Only pending requests can be approved", ex.getMessage());
    }

    @Test
    void rejectLeaveRequest_AlreadyApproved_ThrowsException() {
        leaveRequest.setStatus(LeaveStatus.APPROVED);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.rejectLeaveRequest(1L, "reason"));
        assertEquals("Only pending requests can be rejected", ex.getMessage());
    }

    @Test
    void rejectLeaveRequest_AlreadyCancelled_ThrowsException() {
        leaveRequest.setStatus(LeaveStatus.CANCELLED);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.rejectLeaveRequest(1L, "reason"));
        assertEquals("Only pending requests can be rejected", ex.getMessage());
    }

    @Test
    void rejectLeaveRequest_AlreadyRejected_ThrowsException() {
        leaveRequest.setStatus(LeaveStatus.REJECTED);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> leaveRequestService.rejectLeaveRequest(1L, "reason"));
        assertEquals("Only pending requests can be rejected", ex.getMessage());
    }

    @Test
    void createLeaveRequest_WeekendEdgeCases() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(false);
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArguments()[0]);

        // Friday to Monday = 2 days
        requestDTO.setStartDate(LocalDate.of(2026, 9, 11));
        requestDTO.setEndDate(LocalDate.of(2026, 9, 14));
        leaveRequestService.createLeaveRequest(requestDTO);

        // Saturday to Monday = 1 day
        requestDTO.setStartDate(LocalDate.of(2026, 9, 12));
        requestDTO.setEndDate(LocalDate.of(2026, 9, 14));
        leaveRequestService.createLeaveRequest(requestDTO);

        // Sunday to Monday = 1 day
        requestDTO.setStartDate(LocalDate.of(2026, 9, 13));
        requestDTO.setEndDate(LocalDate.of(2026, 9, 14));
        leaveRequestService.createLeaveRequest(requestDTO);

        // Friday to Sunday = 1 day
        requestDTO.setStartDate(LocalDate.of(2026, 9, 11));
        requestDTO.setEndDate(LocalDate.of(2026, 9, 13));
        leaveRequestService.createLeaveRequest(requestDTO);
        
        verify(leaveRequestRepository, times(4)).save(any(LeaveRequest.class));
    }

    @Test
    void createLeaveRequest_FridayToMonday_ExactBalance_Success() {
        leaveBalance.setAvailableDays(2);
        requestDTO.setStartDate(LocalDate.of(2026, 9, 11)); // Friday
        requestDTO.setEndDate(LocalDate.of(2026, 9, 14)); // Monday

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(false);
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArguments()[0]);

        LeaveResponseDTO response = leaveRequestService.createLeaveRequest(requestDTO);
        assertNotNull(response);
    }

    @Test
    void createLeaveRequest_FridayToMonday_InsufficientBalance_ThrowsException() {
        leaveBalance.setAvailableDays(1); // Need 2
        requestDTO.setStartDate(LocalDate.of(2026, 9, 11)); // Friday
        requestDTO.setEndDate(LocalDate.of(2026, 9, 14)); // Monday

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveRequestRepository.hasOverlappingLeave(any(), any(), any(), any())).thenReturn(false);
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));

        assertThrows(InsufficientLeaveException.class, () -> leaveRequestService.createLeaveRequest(requestDTO));
    }

    @Test
    void cancelLeaveRequest_ApproveThenCancel_BalanceRestored() {
        // 1. Initial State
        leaveBalance.setAvailableDays(10);
        leaveRequest.setStartDate(LocalDate.of(2026, 9, 16)); // Wed
        leaveRequest.setEndDate(LocalDate.of(2026, 9, 18)); // Fri (3 days)
        leaveRequest.setStatus(LeaveStatus.PENDING);
        
        // 2. Approve
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leaveRequest));
        when(leaveBalanceRepository.findByEmployeeAndLeaveType(employee, leaveType)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArguments()[0]);
        
        LeaveResponseDTO approved = leaveRequestService.approveLeaveRequest(1L);
        assertEquals(LeaveStatus.APPROVED, approved.getStatus());
        assertEquals(7, leaveBalance.getAvailableDays()); // Balance deducted
        
        // 3. Cancel
        LeaveResponseDTO cancelled = leaveRequestService.cancelLeaveRequest(1L);
        assertEquals(LeaveStatus.CANCELLED, cancelled.getStatus());
        assertEquals(10, leaveBalance.getAvailableDays()); // Balance restored
        
        // 4. Cancel again
        assertThrows(IllegalArgumentException.class, () -> leaveRequestService.cancelLeaveRequest(1L));
        assertEquals(10, leaveBalance.getAvailableDays()); // Balance remains 10
    }
}
