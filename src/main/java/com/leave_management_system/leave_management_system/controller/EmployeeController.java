package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.service.EmployeeService;

import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    // "EmployeeService contains the business logic for employee operations."
    private final EmployeeService employeeService;

    // "Constructor injection injects EmployeeService into the controller."
    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    public Employee createEmployee(@Valid @RequestBody Employee employee) {

        // "@RequestBody converts the JSON request body into an Employee object."
        return employeeService.createEmployee(employee);
    }

    @GetMapping
    public List<Employee> getAllEmployees(@RequestParam(required = false) String search) {
        if (search != null && !search.trim().isEmpty()) {
            return employeeService.searchEmployees(search);
        }
        // "Returns all employees stored in the database."
        return employeeService.getAllEmployees();
    }

    @GetMapping("/{id}")
    public Employee getEmployeeById(@PathVariable Long id) {

        // "@PathVariable gets the employee ID from the URL."
        return employeeService.getEmployeeById(id);
    }

    @PutMapping("/{id}")
    public Employee updateEmployee(
            @PathVariable Long id,
            @RequestBody Employee employee) {

        // "Updates an existing employee using the employee ID."
        return employeeService.updateEmployee(id, employee);
    }

    @PatchMapping("/{id}/status")
    public Employee changeEmployeeStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {

        // "Changes only the active/inactive status of an employee."
        return employeeService.changeEmployeeStatus(id, active);
    }

    @PutMapping("/{id}/department")
    public Employee transferEmployee(
            @PathVariable Long id,
            @RequestParam Long departmentId) {
        return employeeService.transferEmployee(id, departmentId);
    }

    @DeleteMapping("/{id}")
    public void deleteEmployee(@PathVariable Long id) {

        // "Deletes the employee using the employee ID."
        employeeService.deleteEmployee(id);
    }
}
