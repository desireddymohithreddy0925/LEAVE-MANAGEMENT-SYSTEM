package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.DepartmentResponseDTO;
import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;

import org.springframework.stereotype.Service;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;

import com.leave_management_system.leave_management_system.dto.DepartmentHierarchyDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.dto.TeamHierarchyDTO;
import org.springframework.context.annotation.Lazy;

import org.springframework.security.access.prepost.PreAuthorize;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;

    public DepartmentService(DepartmentRepository departmentRepository,
                             EmployeeRepository employeeRepository,
                             @Lazy EmployeeService employeeService) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
    }

    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "DEPARTMENT_CREATED", entityType = "Department")
    public DepartmentResponseDTO createDepartment(DepartmentRequestDTO dto) {
        if (departmentRepository.existsByName(dto.getName())) {
            throw new DuplicateResourceException("Department already exists");
        }

        Department department = new Department();
        department.setName(dto.getName());
        return DepartmentResponseDTO.fromEntity(departmentRepository.save(department));
    }

    @PreAuthorize("hasAuthority('DEPARTMENT_VIEW')")
    public List<DepartmentResponseDTO> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(DepartmentResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('DEPARTMENT_VIEW')")
    public DepartmentResponseDTO getDepartmentById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
        return DepartmentResponseDTO.fromEntity(department);
    }

    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "DEPARTMENT_UPDATED", entityType = "Department")
    public DepartmentResponseDTO updateDepartment(Long id, DepartmentRequestDTO dto) {
        Department existingDepartment = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));

        if (!existingDepartment.getName().equals(dto.getName()) && departmentRepository.existsByName(dto.getName())) {
            throw new DuplicateResourceException("Department already exists");
        }

        existingDepartment.setName(dto.getName());
        return DepartmentResponseDTO.fromEntity(departmentRepository.save(existingDepartment));
    }

    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "DEPARTMENT_DELETED", entityType = "Department")
    public void deleteDepartment(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));

        if (employeeRepository.existsByDepartmentId(id)) {
            throw new IllegalStateException("Cannot delete department because it still has employees assigned");
        }

        departmentRepository.delete(department);
    }

    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "DEPARTMENT_UPDATED", entityType = "Department")
    public DepartmentResponseDTO assignManager(Long departmentId, Long managerId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + departmentId));

        if (managerId != null) {
            Employee manager = employeeRepository.findById(managerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + managerId));
            
            if (!"ACTIVE".equals(manager.getStatus())) {
                throw new IllegalStateException("Cannot assign an inactive employee as manager");
            }
            
            department.setManager(manager);
        } else {
            department.setManager(null);
        }

        return DepartmentResponseDTO.fromEntity(departmentRepository.save(department));
    }

    @PreAuthorize("hasAuthority('DEPARTMENT_VIEW')")
    public DepartmentHierarchyDTO getDepartmentHierarchy(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));

        List<Employee> allEmployees = employeeRepository.findByDepartmentId(id);
        List<EmployeeResponseDTO> employeeDTOs = allEmployees.stream()
                .map(EmployeeResponseDTO::fromEntity)
                .collect(Collectors.toList());

        List<TeamHierarchyDTO> teams = new ArrayList<>();
        if (department.getManager() != null) {
            teams.add(employeeService.getTeamHierarchy(department.getManager().getId()));
        }

        return new DepartmentHierarchyDTO(
                department.getId(),
                department.getName(),
                department.getManager() != null ? EmployeeResponseDTO.fromEntity(department.getManager()) : null,
                employeeDTOs,
                teams,
                allEmployees.size()
        );
    }

    @PreAuthorize("hasAuthority('DEPARTMENT_VIEW')")
    public List<DepartmentHierarchyDTO> getAllDepartmentHierarchies() {
        return departmentRepository.findAll().stream()
                .map(dept -> getDepartmentHierarchy(dept.getId()))
                .collect(Collectors.toList());
    }
}