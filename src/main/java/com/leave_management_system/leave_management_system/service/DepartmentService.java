package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DepartmentService {

    // "Repository is used to communicate with the database."
    private final DepartmentRepository departmentRepository;

    // "Constructor injection is used to inject DepartmentRepository."
    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public Department createDepartment(Department department) {

        // "save() inserts the new department into the database."
        return departmentRepository.save(department);
    }

    public List<Department> getAllDepartments() {

        // "findAll() retrieves all departments from the database."
        return departmentRepository.findAll();
    }

    public Department getDepartmentById(Long id) {

        // "findById() searches for a department using its primary-key ID."
        return departmentRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Department not found"));
    }

    public Department updateDepartment(Long id, Department updatedDepartment) {

        // "First, find the existing department using its ID."
        Department department = departmentRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Department not found"));

        // "Update the department name with the new value."
        department.setName(updatedDepartment.getName());

        // "save() updates the existing department in the database."
        return departmentRepository.save(department);
    }

    public void deleteDepartment(Long id) {

        // "Check whether the department exists before deleting it."
        if (!departmentRepository.existsById(id)) {
            throw new RuntimeException("Department not found");
        }

        // "deleteById() removes the department using its ID."
        departmentRepository.deleteById(id);
    }
}