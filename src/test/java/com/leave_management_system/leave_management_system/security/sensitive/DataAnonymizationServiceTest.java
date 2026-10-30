package com.leave_management_system.leave_management_system.security.sensitive;

import com.leave_management_system.leave_management_system.dto.AnonymizeResponseDTO;
import com.leave_management_system.leave_management_system.dto.PersonalDataExportDTO;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataAnonymizationServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DataAnonymizationService dataAnonymizationService;

    private Employee employee;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("jane.doe@example.com");
        user.setPassword("hashedPass");
        user.setActive(true);

        employee = new Employee();
        employee.setId(10L);
        employee.setFirstName("Jane");
        employee.setLastName("Doe");
        employee.setPhone("+1234567890");
        employee.setSalary(new BigDecimal("75000.00"));
        employee.setStatus("ACTIVE");
        employee.setUser(user);
    }

    @Test
    @DisplayName("anonymizeEmployee: redacts PII, deactivates user account, and logs audit")
    void anonymizeEmployee_Success() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        AnonymizeResponseDTO response = dataAnonymizationService.anonymizeEmployee(10L);

        assertNotNull(response);
        assertEquals(10L, response.getEmployeeId());
        assertEquals("ANONYMIZED", response.getStatus());

        assertEquals("Anonymized", employee.getFirstName());
        assertEquals("Employee", employee.getLastName());
        assertNull(employee.getPhone());
        assertEquals(BigDecimal.ZERO, employee.getSalary());
        assertEquals("INACTIVE", employee.getStatus());

        assertEquals("anonymized.10@anonymized.local", user.getEmail());
        assertEquals("ANONYMIZED_DISABLED", user.getPassword());
        assertFalse(user.isActive());

        verify(employeeRepository, times(1)).save(employee);
        verify(userRepository, times(1)).save(user);
        verify(auditService, times(1)).logAction(eq("DATA_ANONYMIZED"), eq("Employee"), eq(10L), eq("jane.doe@example.com"), eq("ANONYMIZED"));
    }

    @Test
    @DisplayName("anonymizeEmployee: throws ResourceNotFoundException if employee does not exist")
    void anonymizeEmployee_NotFound_ThrowsException() {
        when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> dataAnonymizationService.anonymizeEmployee(999L));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("exportPersonalData: compiles employee personal data export")
    void exportPersonalData_Success() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(loginHistoryRepository.findTop10ByUserIdOrderByLoginTimeDesc(1L)).thenReturn(Collections.emptyList());

        PersonalDataExportDTO export = dataAnonymizationService.exportPersonalData(10L);

        assertNotNull(export);
        assertEquals(10L, export.getEmployee().getId());
        assertEquals("Jane", export.getEmployee().getFirstName());
        assertEquals(1L, export.getUser().getId());
    }

    @Test
    @DisplayName("exportPersonalData: throws ResourceNotFoundException when employee not found")
    void exportPersonalData_NotFound_ThrowsException() {
        when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> dataAnonymizationService.exportPersonalData(999L));
    }
}

