package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.DepartmentResponseDTO;
import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private Department department;
    private DepartmentRequestDTO requestDTO;

    @BeforeEach
    void setUp() {
        department = new Department();
        department.setName("IT");
        department.setId(1L);

        requestDTO = new DepartmentRequestDTO();
        requestDTO.setName("IT");
    }

    @Test
    void createDepartment_Success() {
        when(departmentRepository.existsByName(requestDTO.getName())).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenReturn(department);

        DepartmentResponseDTO savedDepartment = departmentService.createDepartment(requestDTO);

        assertNotNull(savedDepartment);
        assertEquals("IT", savedDepartment.getName());
        verify(departmentRepository, times(1)).save(any(Department.class));
    }

    @Test
    void createDepartment_ThrowsDuplicateResourceException() {
        when(departmentRepository.existsByName(requestDTO.getName())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> departmentService.createDepartment(requestDTO));
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    void getDepartmentById_Success() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        DepartmentResponseDTO foundDepartment = departmentService.getDepartmentById(1L);

        assertNotNull(foundDepartment);
        assertEquals(1L, foundDepartment.getId());
    }

    @Test
    void getDepartmentById_ThrowsResourceNotFoundException() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> departmentService.getDepartmentById(1L));
    }
}
