package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;

import org.springframework.stereotype.Service;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.entity.Role;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            RoleRepository roleRepository) {

        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public EmployeeResponseDTO createEmployee(EmployeeRequestDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword("ChangeMe123!"); // Default password, should ideally be hashed
        user.setActive(true);

        Role employeeRole = roleRepository.findByName("EMPLOYEE")
            .orElseGet(() -> {
                Role r = new Role("EMPLOYEE");
                return roleRepository.save(r);
            });
        user.getRoles().add(employeeRole);

        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        employee.setPhone(dto.getPhone());
        employee.setEmployeeCode(dto.getEmployeeCode());
        employee.setStatus(dto.getStatus());
        employee.setSalary(dto.getSalary());
        employee.setUser(user);

        if (dto.getDepartmentId() != null) {
            Department department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + dto.getDepartmentId()));
            employee.setDepartment(department);
        }

        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    public List<EmployeeResponseDTO> getAllEmployees() {
        return employeeRepository.findAll().stream()
                .map(EmployeeResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public EmployeeResponseDTO getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        return EmployeeResponseDTO.fromEntity(employee);
    }

    public EmployeeResponseDTO updateEmployee(Long id, EmployeeRequestDTO dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        userRepository.findByEmail(dto.getEmail()).ifPresent(existingUser -> {
            if (employee.getUser() == null || !existingUser.getId().equals(employee.getUser().getId())) {
                throw new DuplicateResourceException("Email already exists");
            }
        });

        if (employee.getUser() != null) {
            employee.getUser().setEmail(dto.getEmail());
        }

        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        employee.setPhone(dto.getPhone());
        employee.setEmployeeCode(dto.getEmployeeCode());
        employee.setStatus(dto.getStatus());
        employee.setSalary(dto.getSalary());

        if (dto.getDepartmentId() != null) {
            Department department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + dto.getDepartmentId()));
            employee.setDepartment(department);
        } else {
            employee.setDepartment(null);
        }

        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    public EmployeeResponseDTO changeEmployeeStatus(Long id, String status) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        employee.setStatus(status);
        if (employee.getUser() != null) {
            employee.getUser().setActive("ACTIVE".equals(status));
        }
        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    public List<EmployeeResponseDTO> searchEmployees(String keyword) {
        return employeeRepository.searchByKeyword(keyword).stream()
                .map(EmployeeResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<EmployeeResponseDTO> getEmployeesByDepartmentId(Long departmentId) {
        return employeeRepository.findByDepartmentId(departmentId).stream()
                .map(EmployeeResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public EmployeeResponseDTO transferEmployee(Long employeeId, Long departmentId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + departmentId));

        employee.setDepartment(department);
        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    public void deleteEmployee(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee not found with id: " + id);
        }
        employeeRepository.deleteById(id);
    }
}