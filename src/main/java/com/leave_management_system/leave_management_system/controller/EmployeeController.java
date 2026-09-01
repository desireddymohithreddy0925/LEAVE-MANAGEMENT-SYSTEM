package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
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
    public EmployeeResponseDTO createEmployee(@Valid @RequestBody EmployeeRequestDTO dto) {
        return employeeService.createEmployee(dto);
    }

    @GetMapping
    public List<EmployeeResponseDTO> getAllEmployees(@RequestParam(required = false) String search) {
        if (search != null && !search.trim().isEmpty()) {
            return employeeService.searchEmployees(search);
        }
        return employeeService.getAllEmployees();
    }

    @GetMapping("/{id}")
    public EmployeeResponseDTO getEmployeeById(@PathVariable Long id) {
        return employeeService.getEmployeeById(id);
    }

    @PutMapping("/{id}")
    public EmployeeResponseDTO updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequestDTO dto) {
        return employeeService.updateEmployee(id, dto);
    }

    @PatchMapping("/{id}/status")
    public EmployeeResponseDTO changeEmployeeStatus(@PathVariable Long id, @RequestParam boolean active) {
        return employeeService.changeEmployeeStatus(id, active);
    }

    @PutMapping("/{id}/department")
    public EmployeeResponseDTO transferEmployee(@PathVariable Long id, @RequestParam Long departmentId) {
        return employeeService.transferEmployee(id, departmentId);
    }

    @DeleteMapping("/{id}")
    public void deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
    }
}
