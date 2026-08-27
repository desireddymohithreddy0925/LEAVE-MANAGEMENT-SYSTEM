package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeService {

    // "EmployeeRepository handles employee database operations."
    private final EmployeeRepository employeeRepository;

    // "DepartmentRepository handles department database operations."
    private final DepartmentRepository departmentRepository;

    // "Constructor injection provides both repositories to the service."
    public EmployeeService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository) {

        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
    }

    public Employee createEmployee(Employee employee) {

        // "Checks whether another employee already uses this email."
        if (employeeRepository.existsByEmail(employee.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // "If a department was provided, verify that it exists."
        if (employee.getDepartment() != null) {

            // "Gets the department ID from the request."
            Long departmentId = employee.getDepartment().getId();

            // "Finds the department from the database."
            Department department = departmentRepository.findById(departmentId)
                    .orElseThrow(() ->
                            new RuntimeException("Department not found"));

            // "Associates the existing department with the employee."
            employee.setDepartment(department);
        }

        // "New employees are active by default."
        employee.setActive(true);

        // "Saves the employee and its department relationship."
        return employeeRepository.save(employee);
    }

    public List<Employee> getAllEmployees() {

        // "Retrieves all employees."
        return employeeRepository.findAll();
    }

    public Employee getEmployeeById(Long id) {

        // "Finds an employee by ID or throws an error if it does not exist."
        return employeeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));
    }

    public Employee updateEmployee(
            Long id,
            Employee updatedEmployee) {

        // "Finds the existing employee."
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));

        // "Updates the employee's basic information."
        employee.setName(updatedEmployee.getName());
        employee.setEmail(updatedEmployee.getEmail());
        employee.setPhone(updatedEmployee.getPhone());

        // "Updates the department if one was provided."
        if (updatedEmployee.getDepartment() != null) {

            // "Gets the department ID from the request."
            Long departmentId =
                    updatedEmployee.getDepartment().getId();

            // "Finds the requested department."
            Department department =
                    departmentRepository.findById(departmentId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Department not found"));

            // "Associates the new department with the employee."
            employee.setDepartment(department);
        }

        // "Saves the updated employee."
        return employeeRepository.save(employee);
    }

    public Employee changeEmployeeStatus(
            Long id,
            boolean active) {

        // "Finds the employee by ID."
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));

        // "Changes the employee's active status."
        employee.setActive(active);

        // "Saves the updated status."
        return employeeRepository.save(employee);
    }

    public void deleteEmployee(Long id) {

        // "Checks whether the employee exists."
        if (!employeeRepository.existsById(id)) {
            throw new RuntimeException("Employee not found");
        }

        // "Deletes the employee from the database."
        employeeRepository.deleteById(id);
    }
}