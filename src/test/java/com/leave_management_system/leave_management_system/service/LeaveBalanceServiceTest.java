package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceResponseDTO;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
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

import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeaveBalanceServiceTest {

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @InjectMocks
    private LeaveBalanceService leaveBalanceService;

    private Employee employee;
    private LeaveType leaveType;
    private LeaveBalance leaveBalance;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);
        employee.setFirstName("John");
        employee.setLastName("Doe");

        leaveType = new LeaveType("Annual Leave", "Desc", 20);
        leaveType.setId(1L);
        leaveType.setActive(true);

        leaveBalance = new LeaveBalance();
        leaveBalance.setId(1L);
        leaveBalance.setEmployee(employee);
        leaveBalance.setLeaveType(leaveType);
        leaveBalance.setYear(Year.now().getValue());
        leaveBalance.setTotal(20);
        leaveBalance.setUsed(0);
        leaveBalance.setAvailable(20);
    }

    @Test
    void createLeaveBalance_Success() {
        int currentYear = Year.now().getValue();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, currentYear)).thenReturn(false);
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenReturn(leaveBalance);

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO();
        req.setEmployeeId(1L);
        req.setLeaveTypeId(1L);

        LeaveBalanceResponseDTO created = leaveBalanceService.createLeaveBalance(req);

        assertNotNull(created);
        assertEquals(20, created.getAvailable());
        assertEquals(20, created.getTotal());
        assertEquals(0, created.getUsed());
        assertEquals(currentYear, created.getYear());
    }

    @Test
    void createLeaveBalance_Success_WithExplicitYearAndTotal() {
        LeaveBalance customBalance = new LeaveBalance(employee, leaveType, 2027, 25, 0, 25);
        customBalance.setId(2L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, 2027)).thenReturn(false);
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenReturn(customBalance);

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(1L, 1L, null, 2027, 25);

        LeaveBalanceResponseDTO created = leaveBalanceService.createLeaveBalance(req);

        assertNotNull(created);
        assertEquals(2027, created.getYear());
        assertEquals(25, created.getTotal());
        assertEquals(25, created.getAvailable());
        assertEquals(0, created.getUsed());
    }

    @Test
    void createLeaveBalance_Success_WithExplicitAvailable() {
        LeaveBalance customBalance = new LeaveBalance(employee, leaveType, 2027, 18, 0, 18);
        customBalance.setId(2L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, 2027)).thenReturn(false);
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenReturn(customBalance);

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(1L, 1L, 18, 2027, null);

        LeaveBalanceResponseDTO created = leaveBalanceService.createLeaveBalance(req);

        assertNotNull(created);
        assertEquals(2027, created.getYear());
        assertEquals(18, created.getTotal());
        assertEquals(18, created.getAvailable());
        assertEquals(0, created.getUsed());
    }

    @Test
    void createLeaveBalance_Success_WithMatchingTotalAndAvailable() {
        LeaveBalance customBalance = new LeaveBalance(employee, leaveType, 2027, 25, 0, 25);
        customBalance.setId(2L);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, 2027)).thenReturn(false);
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenReturn(customBalance);

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(1L, 1L, 25, 2027, 25);

        LeaveBalanceResponseDTO created = leaveBalanceService.createLeaveBalance(req);

        assertNotNull(created);
        assertEquals(2027, created.getYear());
        assertEquals(25, created.getTotal());
        assertEquals(25, created.getAvailable());
        assertEquals(0, created.getUsed());
    }

    @Test
    void createLeaveBalance_Regression_InconsistentTotalAndAvailable_ThrowsException() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, 2027)).thenReturn(false);

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(1L, 1L, 20, 2027, 25);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.createLeaveBalance(req));
        assertTrue(ex.getMessage().contains("must equal total days"));
    }

    @Test
    void createLeaveBalance_Regression_AvailableExceedsTotal_ThrowsException() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, 2027)).thenReturn(false);

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(1L, 1L, 30, 2027, 25);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.createLeaveBalance(req));
        assertTrue(ex.getMessage().contains("Available days cannot exceed total days"));
    }

    @Test
    void createLeaveBalance_NegativeTotal_ThrowsException() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, 2027)).thenReturn(false);

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(1L, 1L, null, 2027, -5);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.createLeaveBalance(req));
        assertTrue(ex.getMessage().contains("Total days cannot be negative"));
    }

    @Test
    void createLeaveBalance_NegativeAvailable_ThrowsException() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, 2027)).thenReturn(false);

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(1L, 1L, -5, 2027, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.createLeaveBalance(req));
        assertTrue(ex.getMessage().contains("Available days cannot be negative"));
    }

    @Test
    void createLeaveBalance_MissingEmployeeId_ThrowsException() {
        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO();
        req.setLeaveTypeId(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.createLeaveBalance(req));
        assertTrue(ex.getMessage().contains("Employee ID is required"));
    }

    @Test
    void createLeaveBalance_MissingLeaveTypeId_ThrowsException() {
        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO();
        req.setEmployeeId(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.createLeaveBalance(req));
        assertTrue(ex.getMessage().contains("Leave Type ID is required"));
    }

    @Test
    void createLeaveBalance_EmployeeNotFound_ThrowsException() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(99L, 1L, 10);

        assertThrows(ResourceNotFoundException.class, () -> leaveBalanceService.createLeaveBalance(req));
    }

    @Test
    void createLeaveBalance_LeaveTypeNotFound_ThrowsException() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(99L)).thenReturn(Optional.empty());

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(1L, 99L, 10);

        assertThrows(ResourceNotFoundException.class, () -> leaveBalanceService.createLeaveBalance(req));
    }

    @Test
    void createLeaveBalance_InactiveLeaveType_ThrowsException() {
        leaveType.setActive(false);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(1L, 1L, 10);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.createLeaveBalance(req));
        assertTrue(ex.getMessage().contains("inactive"));
    }

    @Test
    void createLeaveBalance_Duplicate_ThrowsException() {
        int currentYear = Year.now().getValue();
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1L)).thenReturn(Optional.of(leaveType));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, currentYear)).thenReturn(true);

        LeaveBalanceRequestDTO req = new LeaveBalanceRequestDTO(1L, 1L, 20);

        assertThrows(DuplicateResourceException.class, () -> leaveBalanceService.createLeaveBalance(req));
    }

    @Test
    void getAllLeaveBalances_Success() {
        when(leaveBalanceRepository.findAll()).thenReturn(List.of(leaveBalance));

        List<LeaveBalanceResponseDTO> list = leaveBalanceService.getAllLeaveBalances();

        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).getId());
    }

    @Test
    void getLeaveBalanceById_Success() {
        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));

        LeaveBalanceResponseDTO result = leaveBalanceService.getLeaveBalanceById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getLeaveBalanceById_NotFound_ThrowsException() {
        when(leaveBalanceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveBalanceService.getLeaveBalanceById(999L));
    }

    @Test
    void getBalancesByEmployee_WithoutYear_ReturnsAll() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveBalanceRepository.findByEmployee(employee)).thenReturn(List.of(leaveBalance));

        List<LeaveBalanceResponseDTO> list = leaveBalanceService.getBalancesByEmployee(1L);

        assertEquals(1, list.size());
        verify(leaveBalanceRepository).findByEmployee(employee);
    }

    @Test
    void getBalancesByEmployee_WithYear_ReturnsYearFiltered() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveBalanceRepository.findByEmployeeAndYear(employee, 2026)).thenReturn(List.of(leaveBalance));

        List<LeaveBalanceResponseDTO> list = leaveBalanceService.getBalancesByEmployee(1L, 2026);

        assertEquals(1, list.size());
        verify(leaveBalanceRepository).findByEmployeeAndYear(employee, 2026);
    }

    @Test
    void getBalancesByEmployee_EmployeeNotFound_ThrowsException() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveBalanceService.getBalancesByEmployee(404L, 2026));
    }

    @Test
    void initializeBalancesForEmployee_Success_OnlyCreatesMissing() {
        LeaveType sickLeave = new LeaveType("Sick Leave", "Medical", 10);
        sickLeave.setId(2L);
        sickLeave.setActive(true);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findByActive(true)).thenReturn(List.of(leaveType, sickLeave));
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, 2026)).thenReturn(true);
        when(leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, sickLeave, 2026)).thenReturn(false);

        LeaveBalance newSickBalance = new LeaveBalance(employee, sickLeave, 2026, 10, 0, 10);
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenReturn(newSickBalance);
        when(leaveBalanceRepository.findByEmployeeAndYear(employee, 2026)).thenReturn(List.of(leaveBalance, newSickBalance));

        List<LeaveBalanceResponseDTO> result = leaveBalanceService.initializeBalancesForEmployee(1L, 2026);

        assertEquals(2, result.size());
        verify(leaveBalanceRepository, times(1)).save(any(LeaveBalance.class));
    }

    @Test
    void initializeBalancesForEmployee_EmployeeNotFound_ThrowsException() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveBalanceService.initializeBalancesForEmployee(404L, null));
    }

    @Test
    void updateLeaveBalance_Success() {
        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveBalanceRequestDTO updateReq = new LeaveBalanceRequestDTO();
        updateReq.setAvailable(15);

        LeaveBalanceResponseDTO updated = leaveBalanceService.updateLeaveBalance(1L, updateReq);

        assertNotNull(updated);
        assertEquals(15, updated.getAvailable());
        assertEquals(20, updated.getTotal());
    }

    @Test
    void updateLeaveBalance_WithTotal_Success() {
        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveBalanceRequestDTO updateReq = new LeaveBalanceRequestDTO();
        updateReq.setAvailable(22);
        updateReq.setTotal(25);

        LeaveBalanceResponseDTO updated = leaveBalanceService.updateLeaveBalance(1L, updateReq);

        assertNotNull(updated);
        assertEquals(22, updated.getAvailable());
        assertEquals(25, updated.getTotal());
    }

    @Test
    void updateLeaveBalance_NegativeAvailable_ThrowsException() {
        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));

        LeaveBalanceRequestDTO updateReq = new LeaveBalanceRequestDTO();
        updateReq.setAvailable(-5);

        assertThrows(IllegalArgumentException.class, () -> leaveBalanceService.updateLeaveBalance(1L, updateReq));
    }

    @Test
    void updateLeaveBalance_NegativeTotal_ThrowsException() {
        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));

        LeaveBalanceRequestDTO updateReq = new LeaveBalanceRequestDTO();
        updateReq.setAvailable(10);
        updateReq.setTotal(-1);

        assertThrows(IllegalArgumentException.class, () -> leaveBalanceService.updateLeaveBalance(1L, updateReq));
    }

    @Test
    void updateLeaveBalance_TotalLessThanAvailable_ThrowsException() {
        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));

        LeaveBalanceRequestDTO updateReq = new LeaveBalanceRequestDTO();
        updateReq.setAvailable(15);
        updateReq.setTotal(10);

        assertThrows(IllegalArgumentException.class, () -> leaveBalanceService.updateLeaveBalance(1L, updateReq));
    }

    @Test
    void updateLeaveBalance_Success_TotalOnly() {
        leaveBalance.setUsed(5);
        leaveBalance.setAvailable(15);
        leaveBalance.setTotal(20);

        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveBalanceRequestDTO updateReq = new LeaveBalanceRequestDTO();
        updateReq.setTotal(25);

        LeaveBalanceResponseDTO updated = leaveBalanceService.updateLeaveBalance(1L, updateReq);

        assertNotNull(updated);
        assertEquals(25, updated.getTotal());
        assertEquals(15, updated.getAvailable());
        assertEquals(5, updated.getUsed());
    }

    @Test
    void updateLeaveBalance_Regression_UsedPlusAvailableExceedsTotal_WhenTotalOmitted_ThrowsException() {
        leaveBalance.setUsed(5);
        leaveBalance.setAvailable(15);
        leaveBalance.setTotal(20);

        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));

        LeaveBalanceRequestDTO updateReq = new LeaveBalanceRequestDTO();
        updateReq.setAvailable(18); // 18 available + 5 used = 23 > 20 total

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.updateLeaveBalance(1L, updateReq));
        assertTrue(ex.getMessage().contains("cannot exceed total days"));
    }

    @Test
    void updateLeaveBalance_Regression_UsedPlusAvailableExceedsTotal_WhenTotalProvided_ThrowsException() {
        leaveBalance.setUsed(5);
        leaveBalance.setAvailable(15);
        leaveBalance.setTotal(20);

        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));

        LeaveBalanceRequestDTO updateReq = new LeaveBalanceRequestDTO();
        updateReq.setAvailable(18);
        updateReq.setTotal(20); // 18 + 5 = 23 > 20

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.updateLeaveBalance(1L, updateReq));
        assertTrue(ex.getMessage().contains("cannot exceed total days"));
    }

    @Test
    void updateLeaveBalance_Regression_ReducingTotalBelowUsedPlusAvailable_ThrowsException() {
        leaveBalance.setUsed(5);
        leaveBalance.setAvailable(15);
        leaveBalance.setTotal(20);

        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));

        LeaveBalanceRequestDTO updateReq = new LeaveBalanceRequestDTO();
        updateReq.setTotal(18); // 15 available + 5 used = 20 > 18 total

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.updateLeaveBalance(1L, updateReq));
        assertTrue(ex.getMessage().contains("cannot exceed total days"));
    }

    @Test
    void updateLeaveBalance_BothTotalAndAvailableNull_ThrowsException() {
        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));

        LeaveBalanceRequestDTO updateReq = new LeaveBalanceRequestDTO();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.updateLeaveBalance(1L, updateReq));
        assertTrue(ex.getMessage().contains("At least one of total or available"));
    }

    @Test
    void deleteLeaveBalance_Success_WhenNoPendingOrApprovedRequests() {
        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.existsByEmployeeAndLeaveTypeAndStatusIn(
                eq(employee), eq(leaveType), eq(List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED))))
                .thenReturn(false);

        leaveBalanceService.deleteLeaveBalance(1L);

        verify(leaveBalanceRepository).delete(leaveBalance);
    }

    @Test
    void deleteLeaveBalance_Blocked_WhenPendingOrApprovedRequestsExist() {
        when(leaveBalanceRepository.findById(1L)).thenReturn(Optional.of(leaveBalance));
        when(leaveRequestRepository.existsByEmployeeAndLeaveTypeAndStatusIn(
                eq(employee), eq(leaveType), eq(List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED))))
                .thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> leaveBalanceService.deleteLeaveBalance(1L));
        assertTrue(ex.getMessage().contains("Cannot delete leave balance with pending or approved leave requests"));
        verify(leaveBalanceRepository, never()).delete(any());
    }

    @Test
    void deleteLeaveBalance_NotFound_ThrowsException() {
        when(leaveBalanceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leaveBalanceService.deleteLeaveBalance(999L));
    }
}
