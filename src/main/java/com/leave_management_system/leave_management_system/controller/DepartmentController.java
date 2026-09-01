package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.DepartmentResponseDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.service.DepartmentService;
import com.leave_management_system.leave_management_system.service.EmployeeService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
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
    public DepartmentResponseDTO createDepartment(@Valid @RequestBody DepartmentRequestDTO dto) {
        return departmentService.createDepartment(dto);
    }

    @GetMapping
    public List<DepartmentResponseDTO> getAllDepartments() {
        return departmentService.getAllDepartments();
    }

    @GetMapping("/{id}")
    public DepartmentResponseDTO getDepartmentById(@PathVariable Long id) {
        return departmentService.getDepartmentById(id);
    }

    @GetMapping("/{id}/employees")
    public List<EmployeeResponseDTO> getDepartmentEmployees(@PathVariable Long id) {
        return employeeService.getEmployeesByDepartmentId(id);
    }

    @PutMapping("/{id}")
    public DepartmentResponseDTO updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequestDTO dto) {
        return departmentService.updateDepartment(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDepartment(
            @PathVariable Long id) {

        // "Deletes the department using its ID."
        departmentService.deleteDepartment(id);
    }
}