package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
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

        employee = new Employee();
        employee.setId(1L);
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setEmail("john@example.com");
        employee.setDepartment(department);

        requestDTO = new EmployeeRequestDTO();
        requestDTO.setFirstName("John");
        requestDTO.setLastName("Doe");
        requestDTO.setEmail("john@example.com");
        requestDTO.setDepartmentId(1L);
    }

    @Test
    void createEmployee_Success() {
        when(employeeRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        EmployeeResponseDTO savedEmployee = employeeService.createEmployee(requestDTO);

        assertNotNull(savedEmployee);
        assertEquals("john@example.com", savedEmployee.getEmail());
    }

    @Test
    void createEmployee_DuplicateEmail_ThrowsException() {
        when(employeeRepository.existsByEmail(requestDTO.getEmail())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> employeeService.createEmployee(requestDTO));
    }

    @Test
    void createEmployee_DepartmentNotFound_ThrowsException() {
        when(employeeRepository.existsByEmail(requestDTO.getEmail())).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> employeeService.createEmployee(requestDTO));
    }
}
