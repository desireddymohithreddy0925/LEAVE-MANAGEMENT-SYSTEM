package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.EmployeeProfileUpdateDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.dto.PasswordChangeRequestDTO;
import com.leave_management_system.leave_management_system.service.AuthService;
import com.leave_management_system.leave_management_system.service.EmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employee API", description = "Endpoints for managing employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AuthService authService;

    public EmployeeController(EmployeeService employeeService, AuthService authService) {
        this.employeeService = employeeService;
        this.authService = authService;
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_CREATE')")
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "Create a new employee", description = "Creates a new employee and assigns them to a department.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Employee created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Department not found"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public EmployeeResponseDTO createEmployee(@Valid @RequestBody EmployeeRequestDTO dto) {
        return employeeService.createEmployee(dto);
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW')")
    @GetMapping
    @Operation(summary = "Get all employees or search by keyword", description = "Returns a paginated list of employees, optionally filtered by a search keyword.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    })
    public Page<EmployeeResponseDTO> getAllEmployees(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        if (search != null && !search.trim().isEmpty()) {
            return employeeService.searchEmployees(search, pageable);
        }
        return employeeService.getAllEmployees(pageable);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    @Operation(summary = "Get current employee profile", description = "Returns the employee profile of the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved profile"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Employee profile not found")
    })
    public EmployeeResponseDTO getMyProfile(Authentication authentication) {
        return employeeService.getMyProfile(authentication);
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/profile")
    @Operation(summary = "Update current employee profile", description = "Allows the authenticated user to update their permitted profile fields (firstName, lastName, phone).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Employee is inactive")
    })
    public EmployeeResponseDTO updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody EmployeeProfileUpdateDTO dto) {
        return employeeService.updateMyProfile(authentication, dto);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/change-password")
    @Operation(summary = "Change password for current user", description = "Changes password for the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password data or current password incorrect"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> changePasswordPost(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeRequestDTO request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok("Password changed successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/password")
    @Operation(summary = "Update password for current user", description = "Updates password for the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password data or current password incorrect"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<String> changePasswordPut(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeRequestDTO request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok("Password changed successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/hierarchy")
    @Operation(summary = "Get current employee reporting hierarchy", description = "Returns the reporting chain upwards and direct reports downwards for the logged-in user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved reporting hierarchy"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public com.leave_management_system.leave_management_system.dto.EmployeeHierarchyDTO getMyHierarchy(
            Authentication authentication) {
        return employeeService.getMyHierarchy(authentication);
    }

    @PreAuthorize("@securityService.canViewEmployee(authentication, #id)")
    @GetMapping("/{id}")
    @Operation(summary = "Get employee by ID", description = "Returns the details of a specific employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved employee"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public EmployeeResponseDTO getEmployeeById(@PathVariable Long id) {
        return employeeService.getEmployeeById(id);
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_UPDATE')")
    @PutMapping("/{id}")
    @Operation(summary = "Update an employee", description = "Updates the details of an existing employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Employee or Department not found"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public EmployeeResponseDTO updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequestDTO dto) {
        return employeeService.updateEmployee(id, dto);
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_UPDATE')")
    @PatchMapping("/{id}/status")
    @Operation(summary = "Change employee status", description = "Activates or deactivates an employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public ResponseEntity<EmployeeResponseDTO> changeStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        return ResponseEntity.ok(employeeService.changeEmployeeStatus(id, status));
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_UPDATE')")
    @PutMapping("/{id}/department")
    @Operation(summary = "Transfer employee to department", description = "Moves an employee to a different department.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee transferred successfully"),
            @ApiResponse(responseCode = "404", description = "Employee or Department not found")
    })
    public EmployeeResponseDTO transferEmployee(@PathVariable Long id, @RequestParam Long departmentId) {
        return employeeService.transferEmployee(id, departmentId);
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_DEACTIVATE')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an employee", description = "Permanently deletes an employee from the system.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot delete employee with existing leave records"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public void deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
    }

    @PreAuthorize("@securityService.isSelf(authentication, #id)")
    @PutMapping("/{id}/profile")
    @Operation(summary = "Update employee profile", description = "Allows an employee to update their own basic profile fields.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public EmployeeResponseDTO updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeProfileUpdateDTO dto) {
        return employeeService.updateProfile(id, dto);
    }

    @PreAuthorize("hasAuthority('EMPLOYEE_UPDATE')")
    @PatchMapping("/{id}/manager")
    @Operation(summary = "Assign manager to employee", description = "Assigns or removes a manager for an employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manager assigned successfully"),
            @ApiResponse(responseCode = "404", description = "Employee or Manager not found")
    })
    public EmployeeResponseDTO assignManager(
            @PathVariable Long id,
            @RequestParam(required = false) Long managerId) {
        return employeeService.assignManager(id, managerId);
    }

    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE')")
    @PatchMapping("/{id}/role")
    @Operation(summary = "Change employee role", description = "Changes the system role of an employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role changed successfully"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public EmployeeResponseDTO changeRole(
            @PathVariable Long id,
            @RequestParam String roleName) {
        return employeeService.changeRole(id, roleName);
    }

    @PreAuthorize("@securityService.canViewEmployee(authentication, #id)")
    @GetMapping("/{id}/hierarchy")
    @Operation(summary = "Get employee reporting hierarchy", description = "Returns the reporting chain upwards and direct reports downwards for the specified employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved employee hierarchy"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public com.leave_management_system.leave_management_system.dto.EmployeeHierarchyDTO getEmployeeHierarchy(@PathVariable Long id) {
        return employeeService.getEmployeeHierarchy(id);
    }
}
