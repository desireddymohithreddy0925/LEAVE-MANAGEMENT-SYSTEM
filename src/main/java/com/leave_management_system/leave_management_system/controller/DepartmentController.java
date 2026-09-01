package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.DepartmentResponseDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.service.DepartmentService;
import com.leave_management_system.leave_management_system.service.EmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@Tag(name = "Department API", description = "Endpoints for managing departments")
public class DepartmentController {

    // "Service contains the business logic for department operations."
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;

    // "Constructor injection injects DepartmentService into the controller."
    public DepartmentController(DepartmentService departmentService, EmployeeService employeeService) {
        this.departmentService = departmentService;
        this.employeeService = employeeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new department", description = "Creates a new department in the system.")
    public DepartmentResponseDTO createDepartment(@Valid @RequestBody DepartmentRequestDTO dto) {
        return departmentService.createDepartment(dto);
    }

    @GetMapping
    @Operation(summary = "Get all departments", description = "Returns a list of all available departments.")
    public List<DepartmentResponseDTO> getAllDepartments() {
        return departmentService.getAllDepartments();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get department by ID", description = "Returns the details of a specific department.")
    public DepartmentResponseDTO getDepartmentById(@PathVariable Long id) {
        return departmentService.getDepartmentById(id);
    }

    @GetMapping("/{id}/employees")
    @Operation(summary = "Get employees in a department", description = "Returns a list of all employees assigned to the specified department.")
    public List<EmployeeResponseDTO> getDepartmentEmployees(@PathVariable Long id) {
        return employeeService.getEmployeesByDepartmentId(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a department", description = "Updates the details of an existing department.")
    public DepartmentResponseDTO updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequestDTO dto) {
        return departmentService.updateDepartment(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a department", description = "Deletes a department. Will fail if the department still has employees.")
    public void deleteDepartment(
            @PathVariable Long id) {

        // "Deletes the department using its ID."
        departmentService.deleteDepartment(id);
    }
}