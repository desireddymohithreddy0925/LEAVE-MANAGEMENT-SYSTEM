package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.service.EmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Employee API", description = "Endpoints for managing employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

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

    @GetMapping
    @Operation(summary = "Get all employees or search by keyword", description = "Returns a list of all employees, optionally filtered by a search keyword (name or email).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    })
    public List<EmployeeResponseDTO> getAllEmployees(@RequestParam(required = false) String search) {
        if (search != null && !search.trim().isEmpty()) {
            return employeeService.searchEmployees(search);
        }
        return employeeService.getAllEmployees();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get employee by ID", description = "Returns the details of a specific employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved employee"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public EmployeeResponseDTO getEmployeeById(@PathVariable Long id) {
        return employeeService.getEmployeeById(id);
    }

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

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change employee status", description = "Activates or deactivates an employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    public EmployeeResponseDTO changeEmployeeStatus(@PathVariable Long id, @RequestParam boolean active) {
        return employeeService.changeEmployeeStatus(id, active);
    }

    @PutMapping("/{id}/department")
    @Operation(summary = "Transfer employee to department", description = "Moves an employee to a different department.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee transferred successfully"),
            @ApiResponse(responseCode = "404", description = "Employee or Department not found")
    })
    public EmployeeResponseDTO transferEmployee(@PathVariable Long id, @RequestParam Long departmentId) {
        return employeeService.transferEmployee(id, departmentId);
    }

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
}
