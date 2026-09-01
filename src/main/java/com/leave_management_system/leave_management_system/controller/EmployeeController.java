package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.service.EmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employee API", description = "Endpoints for managing employees")
public class EmployeeController {

    // "EmployeeService contains the business logic for employee operations."
    private final EmployeeService employeeService;

    // "Constructor injection injects EmployeeService into the controller."
    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "Create a new employee", description = "Creates a new employee and assigns them to a department.")
    public EmployeeResponseDTO createEmployee(@Valid @RequestBody EmployeeRequestDTO dto) {
        return employeeService.createEmployee(dto);
    }

    @GetMapping
    @Operation(summary = "Get all employees or search by keyword", description = "Returns a list of all employees, optionally filtered by a search keyword (name or email).")
    public List<EmployeeResponseDTO> getAllEmployees(@RequestParam(required = false) String search) {
        if (search != null && !search.trim().isEmpty()) {
            return employeeService.searchEmployees(search);
        }
        return employeeService.getAllEmployees();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get employee by ID", description = "Returns the details of a specific employee.")
    public EmployeeResponseDTO getEmployeeById(@PathVariable Long id) {
        return employeeService.getEmployeeById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an employee", description = "Updates the details of an existing employee.")
    public EmployeeResponseDTO updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequestDTO dto) {
        return employeeService.updateEmployee(id, dto);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change employee status", description = "Activates or deactivates an employee.")
    public EmployeeResponseDTO changeEmployeeStatus(@PathVariable Long id, @RequestParam boolean active) {
        return employeeService.changeEmployeeStatus(id, active);
    }

    @PutMapping("/{id}/department")
    @Operation(summary = "Transfer employee to department", description = "Moves an employee to a different department.")
    public EmployeeResponseDTO transferEmployee(@PathVariable Long id, @RequestParam Long departmentId) {
        return employeeService.transferEmployee(id, departmentId);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an employee", description = "Permanently deletes an employee from the system.")
    public void deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
    }
}
