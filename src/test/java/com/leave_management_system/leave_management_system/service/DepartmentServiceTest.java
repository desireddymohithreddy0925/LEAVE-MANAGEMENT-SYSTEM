package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
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
public class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private Department department;

    @BeforeEach
    void setUp() {
        department = new Department();
        department.setName("IT");
        department.setId(1L);
    }

    @Test
    void createDepartment_Success() {
        when(departmentRepository.existsByName(department.getName())).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenReturn(department);

        Department savedDepartment = departmentService.createDepartment(department);

        assertNotNull(savedDepartment);
        assertEquals("IT", savedDepartment.getName());
        verify(departmentRepository, times(1)).save(department);
    }

    @Test
    void createDepartment_ThrowsDuplicateResourceException() {
        when(departmentRepository.existsByName(department.getName())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> departmentService.createDepartment(department));
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    void getDepartmentById_Success() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        Department foundDepartment = departmentService.getDepartmentById(1L);

        assertNotNull(foundDepartment);
        assertEquals(1L, foundDepartment.getId());
    }

    @Test
    void getDepartmentById_ThrowsResourceNotFoundException() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> departmentService.getDepartmentById(1L));
    }
}
