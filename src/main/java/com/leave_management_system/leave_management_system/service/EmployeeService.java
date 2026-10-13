package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.EmployeeProfileUpdateDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.dto.TeamHierarchyDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeHierarchyDTO;
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

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasAuthority('EMPLOYEE_UPDATE')")
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

    @PreAuthorize("hasAuthority('EMPLOYEE_CREATE')")
    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "EMPLOYEE_CREATED", entityType = "Employee")
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

        if (dto.getManagerId() != null) {
            Employee manager = employeeRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + dto.getManagerId()));
            if (!"ACTIVE".equals(manager.getStatus())) {
                throw new IllegalStateException("Cannot assign an inactive employee as manager");
            }
            employee.setManager(manager);
        }

        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW')")
    public Page<EmployeeResponseDTO> getAllEmployees(Pageable pageable) {
        return employeeRepository.findAll(pageable)
                .map(EmployeeResponseDTO::fromEntity);
    }

    @PreAuthorize("@securityService.canViewEmployee(authentication, #id)")
    public EmployeeResponseDTO getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        return EmployeeResponseDTO.fromEntity(employee);
    }

    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "EMPLOYEE_UPDATED", entityType = "Employee")
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

        if (dto.getManagerId() != null) {
            if (id.equals(dto.getManagerId())) {
                throw new IllegalArgumentException("An employee cannot be their own manager");
            }
            Employee manager = employeeRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + dto.getManagerId()));
            if (!"ACTIVE".equals(manager.getStatus())) {
                throw new IllegalStateException("Cannot assign an inactive employee as manager");
            }
            validateNoCircularReporting(employee, manager);
            employee.setManager(manager);
        }

        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "EMPLOYEE_DEACTIVATED", entityType = "Employee")
    public EmployeeResponseDTO changeEmployeeStatus(Long id, String status) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        employee.setStatus(status);
        if (employee.getUser() != null) {
            employee.getUser().setActive("ACTIVE".equals(status));
        }
        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW')")
    public Page<EmployeeResponseDTO> searchEmployees(String keyword, Pageable pageable) {
        return employeeRepository.searchByKeyword(keyword, pageable)
                .map(EmployeeResponseDTO::fromEntity);
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW')")
    public List<EmployeeResponseDTO> getEmployeesByDepartmentId(Long departmentId) {
        return employeeRepository.findByDepartmentId(departmentId).stream()
                .map(EmployeeResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "EMPLOYEE_UPDATED", entityType = "Employee")
    public EmployeeResponseDTO transferEmployee(Long employeeId, Long departmentId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + departmentId));

        // If the employee was the manager of their old department, disassociate old department manager
        Department oldDept = employee.getDepartment();
        if (oldDept != null && oldDept.getManager() != null && oldDept.getManager().getId().equals(employee.getId())) {
            oldDept.setManager(null);
            departmentRepository.save(oldDept);
        }

        employee.setDepartment(department);
        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_DEACTIVATE')")
    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "EMPLOYEE_DEACTIVATED", entityType = "Employee")
    public void deleteEmployee(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee not found with id: " + id);
        }
        employeeRepository.deleteById(id);
    }

    @PreAuthorize("@securityService.isSelf(authentication, #id)")
    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "EMPLOYEE_UPDATED", entityType = "Employee")
    public EmployeeResponseDTO updateProfile(Long id, EmployeeProfileUpdateDTO dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new IllegalStateException("Cannot update profile of an inactive employee");
        }

        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        employee.setPhone(dto.getPhone());
        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    @PreAuthorize("isAuthenticated()")
    public EmployeeResponseDTO getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        Employee employee = employeeRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for authenticated user"));
        return EmployeeResponseDTO.fromEntity(employee);
    }

    @PreAuthorize("isAuthenticated()")
    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "EMPLOYEE_UPDATED", entityType = "Employee")
    public EmployeeResponseDTO updateMyProfile(Authentication authentication, EmployeeProfileUpdateDTO dto) {
        String email = authentication.getName();
        Employee employee = employeeRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for authenticated user"));

        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new IllegalStateException("Cannot update profile of an inactive employee");
        }

        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        employee.setPhone(dto.getPhone());
        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    @PreAuthorize("isAuthenticated()")
    public EmployeeHierarchyDTO getMyHierarchy(Authentication authentication) {
        String email = authentication.getName();
        Employee employee = employeeRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee profile not found for authenticated user"));
        return getEmployeeHierarchy(employee.getId());
    }

    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "EMPLOYEE_UPDATED", entityType = "Employee")
    public EmployeeResponseDTO assignManager(Long employeeId, Long managerId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new IllegalStateException("Cannot assign a manager to an inactive employee");
        }

        if (managerId != null) {
            if (employeeId.equals(managerId)) {
                throw new IllegalArgumentException("An employee cannot be their own manager");
            }
            Employee manager = employeeRepository.findById(managerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + managerId));
            if (!"ACTIVE".equals(manager.getStatus())) {
                throw new IllegalStateException("Cannot assign an inactive employee as manager");
            }
            validateNoCircularReporting(employee, manager);
            employee.setManager(manager);
        } else {
            employee.setManager(null);
        }

        return EmployeeResponseDTO.fromEntity(employeeRepository.save(employee));
    }

    private void validateNoCircularReporting(Employee employee, Employee prospectiveManager) {
        Employee current = prospectiveManager;
        Set<Long> visited = new HashSet<>();
        while (current != null) {
            if (employee.getId() != null && employee.getId().equals(current.getId())) {
                throw new IllegalArgumentException("Circular manager assignment is not allowed: employee is an ancestor of the proposed manager");
            }
            if (!visited.add(current.getId())) {
                break;
            }
            current = current.getManager();
        }
    }

    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "ROLE_CHANGED", entityType = "Employee")
    public EmployeeResponseDTO changeRole(Long id, String roleName) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        if (employee.getUser() == null) {
            throw new IllegalStateException("Employee has no associated user account");
        }

        Role newRole = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));

        employee.getUser().getRoles().clear();
        employee.getUser().getRoles().add(newRole);

        userRepository.save(employee.getUser());
        return EmployeeResponseDTO.fromEntity(employee);
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW')")
    public List<EmployeeResponseDTO> getManagerTeam(Long managerId) {
        return employeeRepository.findTeamByManagerId(managerId).stream()
                .map(EmployeeResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW') or hasAuthority('LEAVE_VIEW_TEAM')")
    public TeamHierarchyDTO getTeamHierarchy(Long managerId) {
        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + managerId));
        return buildTeamHierarchy(manager, new HashSet<>());
    }

    private TeamHierarchyDTO buildTeamHierarchy(Employee manager, Set<Long> visitedManagers) {
        if (!visitedManagers.add(manager.getId())) {
            return new TeamHierarchyDTO(manager.getId(),
                    manager.getFirstName() + " " + manager.getLastName(),
                    manager.getUser() != null ? manager.getUser().getEmail() : null,
                    manager.getDepartment() != null ? manager.getDepartment().getName() : null,
                    new ArrayList<>(), new ArrayList<>(), 0);
        }

        List<Employee> directReportsList = employeeRepository.findTeamByManagerId(manager.getId());
        List<EmployeeResponseDTO> directReportDTOs = directReportsList.stream()
                .map(EmployeeResponseDTO::fromEntity)
                .collect(Collectors.toList());

        List<TeamHierarchyDTO> subTeams = new ArrayList<>();
        for (Employee report : directReportsList) {
            List<Employee> subReports = employeeRepository.findByManagerId(report.getId());
            if (!subReports.isEmpty() && !visitedManagers.contains(report.getId())) {
                subTeams.add(buildTeamHierarchy(report, new HashSet<>(visitedManagers)));
            }
        }

        int totalSubordinates = countSubordinates(manager);

        return new TeamHierarchyDTO(
                manager.getId(),
                manager.getFirstName() + " " + manager.getLastName(),
                manager.getUser() != null ? manager.getUser().getEmail() : null,
                manager.getDepartment() != null ? manager.getDepartment().getName() : null,
                directReportDTOs,
                subTeams,
                totalSubordinates
        );
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW') or hasAuthority('LEAVE_VIEW_TEAM')")
    public int countSubordinates(Long managerId) {
        if (managerId == null) {
            return 0;
        }
        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + managerId));
        return countSubordinates(manager);
    }

    public int countSubordinates(Employee manager) {
        if (manager == null || manager.getId() == null) {
            return 0;
        }
        Set<Long> visited = new HashSet<>();
        visited.add(manager.getId()); // Cycle protection: seed root manager so they cannot be counted as subordinate
        collectAllSubordinateIds(manager.getId(), visited);
        return Math.max(0, visited.size() - 1);
    }

    private void collectAllSubordinateIds(Long managerId, Set<Long> visited) {
        if (managerId == null) {
            return;
        }
        List<Employee> reports = employeeRepository.findTeamByManagerId(managerId);
        for (Employee emp : reports) {
            if (emp.getId() != null && visited.add(emp.getId())) {
                collectAllSubordinateIds(emp.getId(), visited);
            }
        }
    }

    @PreAuthorize("@securityService.canViewEmployee(authentication, #employeeId)")
    public EmployeeHierarchyDTO getEmployeeHierarchy(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        List<EmployeeResponseDTO> reportingChain = new ArrayList<>();
        Employee current = employee.getManager();
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current.getId())) {
            reportingChain.add(EmployeeResponseDTO.fromEntity(current));
            current = current.getManager();
        }

        List<EmployeeResponseDTO> directReports = employeeRepository.findByManagerId(employeeId).stream()
                .map(EmployeeResponseDTO::fromEntity)
                .collect(Collectors.toList());

        return new EmployeeHierarchyDTO(EmployeeResponseDTO.fromEntity(employee), reportingChain, directReports);
    }
}