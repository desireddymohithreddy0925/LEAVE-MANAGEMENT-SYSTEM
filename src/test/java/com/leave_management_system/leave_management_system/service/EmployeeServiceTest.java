package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.entity.Role;
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
public class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee;
    private Department department;
    private EmployeeRequestDTO requestDTO;

    @BeforeEach
    void setUp() {
        department = new Department();
        department.setName("IT");
        department.setId(1L);

        User user = new User();
        user.setId(1L);
        user.setEmail("jane.doe@example.com");

        employee = new Employee();
        employee.setId(1L);
        employee.setFirstName("Jane");
        employee.setLastName("Doe");
        employee.setPhone("1234567890");
        employee.setEmployeeCode("EMP-001");
        employee.setStatus("ACTIVE");
        employee.setSalary(new java.math.BigDecimal("50000"));
        employee.setDepartment(department);
        employee.setUser(user);

        requestDTO = new EmployeeRequestDTO();
        requestDTO.setFirstName("John");
        requestDTO.setLastName("Doe");
        requestDTO.setEmail("jane.doe@example.com");
        requestDTO.setEmployeeCode("EMP-001");
        requestDTO.setStatus("ACTIVE");
        requestDTO.setSalary(new java.math.BigDecimal("50000"));
        requestDTO.setDepartmentId(1L);
    }

    @Test
    void createEmployee_Success() {
        when(userRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(new Role("EMPLOYEE")));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(userRepository.save(any(User.class))).thenReturn(employee.getUser());
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        EmployeeResponseDTO savedEmployee = employeeService.createEmployee(requestDTO);

        assertNotNull(savedEmployee);
        assertEquals("jane.doe@example.com", savedEmployee.getEmail());
    }

    @Test
    void createEmployee_DuplicateEmail_ThrowsException() {
        when(userRepository.existsByEmail("jane.doe@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> employeeService.createEmployee(requestDTO));
    }

    @Test
    void createEmployee_DepartmentNotFound_ThrowsException() {
        when(userRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(new Role("EMPLOYEE")));
        when(departmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> employeeService.createEmployee(requestDTO));
    }

    @Test
    void updateEmployee_DuplicateEmail_ThrowsException() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("jane.doe@example.com");

        Employee existingOther = new Employee();
        existingOther.setId(2L);
        existingOther.setUser(otherUser);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(userRepository.findByEmail("jane.doe@example.com")).thenReturn(Optional.of(otherUser));

        assertThrows(DuplicateResourceException.class, () -> employeeService.updateEmployee(1L, requestDTO));
    }

    @Test
    void searchEmployees_Success() {
        when(employeeRepository.searchByKeyword("john")).thenReturn(java.util.List.of(employee));

        java.util.List<EmployeeResponseDTO> results = employeeService.searchEmployees("john");

        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals("jane.doe@example.com", results.get(0).getEmail());
    }
}
