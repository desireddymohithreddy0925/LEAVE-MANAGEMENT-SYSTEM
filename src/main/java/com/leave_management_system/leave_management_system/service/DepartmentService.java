package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.DepartmentResponseDTO;
import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;

import org.springframework.stereotype.Service;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    // "Repository is used to communicate with the database."
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    // "Constructor injection is used to inject DepartmentRepository."
    public DepartmentService(DepartmentRepository departmentRepository, EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    public DepartmentResponseDTO createDepartment(DepartmentRequestDTO dto) {
        if (departmentRepository.existsByName(dto.getName())) {
            throw new DuplicateResourceException("Department already exists");
        }

        Department department = new Department();
        department.setName(dto.getName());
        return DepartmentResponseDTO.fromEntity(departmentRepository.save(department));
    }

    public List<DepartmentResponseDTO> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(DepartmentResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public DepartmentResponseDTO getDepartmentById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
        return DepartmentResponseDTO.fromEntity(department);
    }

    public DepartmentResponseDTO updateDepartment(Long id, DepartmentRequestDTO dto) {
        Department existingDepartment = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));

        existingDepartment.setName(dto.getName());
        return DepartmentResponseDTO.fromEntity(departmentRepository.save(existingDepartment));
    }

    public void deleteDepartment(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));

        if (employeeRepository.existsByDepartmentId(id)) {
            throw new IllegalStateException("Cannot delete department because it still has employees assigned");
        }

        // "delete() removes the department."
        departmentRepository.delete(department);
    }
}