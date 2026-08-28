package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.service.DepartmentService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    // "Service contains the business logic for department operations."
    private final DepartmentService departmentService;

    // "Constructor injection injects DepartmentService into the controller."
    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Department createDepartment(
            @Valid @RequestBody Department department) {

        // "@RequestBody converts the JSON request body into a Department object."
        return departmentService.createDepartment(department);
    }

    @GetMapping
    public List<Department> getAllDepartments() {

        // "Returns all departments from the database."
        return departmentService.getAllDepartments();
    }

    @GetMapping("/{id}")
    public Department getDepartmentById(
            @PathVariable Long id) {

        // "@PathVariable gets the department ID from the URL."
        return departmentService.getDepartmentById(id);
    }

    @PutMapping("/{id}")
    public Department updateDepartment(
            @PathVariable Long id,
            @RequestBody Department department) {

        // "Updates an existing department using its ID."
        return departmentService.updateDepartment(id, department);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDepartment(
            @PathVariable Long id) {

        // "Deletes the department using its ID."
        departmentService.deleteDepartment(id);
    }
}