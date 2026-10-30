package com.leave_management_system.leave_management_system.security;

import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.TransactionReviewRepository;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityAuthorizationServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private AuthorizationAuditService authorizationAuditService;

    @Mock
    private TransactionReviewRepository transactionReviewRepository;

    @InjectMocks
    private SecurityAuthorizationService securityAuthorizationService;

    private Employee employee;
    private Employee manager;
    private Employee director;
    private Department department;

    @BeforeEach
    void setUp() {
        director = new Employee();
        director.setId(100L);

        manager = new Employee();
        manager.setId(20L);
        manager.setManager(director);

        department = new Department();
        department.setId(1L);
        department.setManager(manager);

        employee = new Employee();
        employee.setId(5L);
        employee.setManager(manager);
        employee.setDepartment(department);
    }

    @Test
    @DisplayName("isSelf: returns true when authenticated employee id matches target employee id")
    void isSelf_TrueWhenIdMatches() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("emp@example.com");
        when(employeeRepository.findByUserEmail("emp@example.com")).thenReturn(Optional.of(employee));

        assertTrue(securityAuthorizationService.isSelf(auth, 5L));
        assertFalse(securityAuthorizationService.isSelf(auth, 10L));
    }

    @Test
    @DisplayName("isManagerOfEmployee: returns true for direct manager")
    void isManagerOfEmployee_DirectManager_ReturnsTrue() {
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));

        assertTrue(securityAuthorizationService.isManagerOfEmployee(20L, 5L));
    }

    @Test
    @DisplayName("isManagerOfEmployee: returns true for department manager")
    void isManagerOfEmployee_DepartmentManager_ReturnsTrue() {
        employee.setManager(null); // No direct manager
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));

        assertTrue(securityAuthorizationService.isManagerOfEmployee(20L, 5L));
    }

    @Test
    @DisplayName("isManagerOfEmployee: returns true for multi-level manager hierarchy")
    void isManagerOfEmployee_HierarchyManager_ReturnsTrue() {
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));

        // Director (id: 100) manages Manager (id: 20) who manages Employee (id: 5)
        assertTrue(securityAuthorizationService.isManagerOfEmployee(100L, 5L));
    }

    @Test
    @DisplayName("isManagerOfEmployee: returns false when managerId is same as employeeId or not manager")
    void isManagerOfEmployee_SameOrNotManager_ReturnsFalse() {
        assertFalse(securityAuthorizationService.isManagerOfEmployee(5L, 5L));
        assertFalse(securityAuthorizationService.isManagerOfEmployee((Long) null, 5L));

        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        assertFalse(securityAuthorizationService.isManagerOfEmployee(999L, 5L));
    }

    @Test
    @DisplayName("canViewEmployee: allows access for ROLE_ADMIN")
    void canViewEmployee_Admin_ReturnsTrue() {
        Authentication auth = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(auth).getAuthorities();

        assertTrue(securityAuthorizationService.canViewEmployee(auth, 5L));
    }

    @Test
    @DisplayName("canViewEmployee: allows access for self")
    void canViewEmployee_Self_ReturnsTrue() {
        Authentication auth = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))).when(auth).getAuthorities();
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("emp@example.com");
        when(employeeRepository.findByUserEmail("emp@example.com")).thenReturn(Optional.of(employee));

        assertTrue(securityAuthorizationService.canViewEmployee(auth, 5L));
    }

    @Test
    @DisplayName("canApproveLeaveRequest: returns true if user has LEAVE_APPROVE and is manager")
    void canApproveLeaveRequest_ManagerWithPermission_ReturnsTrue() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("LEAVE_APPROVE"))).when(auth).getAuthorities();
        when(auth.getName()).thenReturn("manager@example.com");
        when(employeeRepository.findByUserEmail("manager@example.com")).thenReturn(Optional.of(manager));

        LeaveRequest lr = new LeaveRequest();
        lr.setId(50L);
        lr.setEmployee(employee);

        when(leaveRequestRepository.findById(50L)).thenReturn(Optional.of(lr));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));

        assertTrue(securityAuthorizationService.canApproveLeaveRequest(auth, 50L));
    }

    @Test
    @DisplayName("canApproveLeaveRequest: returns false if user lacks LEAVE_APPROVE permission")
    void canApproveLeaveRequest_NoPermission_ReturnsFalse() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("LEAVE_VIEW"))).when(auth).getAuthorities();

        assertFalse(securityAuthorizationService.canApproveLeaveRequest(auth, 50L));
    }
}
