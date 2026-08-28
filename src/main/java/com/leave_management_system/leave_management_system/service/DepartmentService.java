package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;

import org.springframework.stereotype.Service;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;

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

        if (departmentRepository.existsByName(department.getName())) {
            throw new DuplicateResourceException("Department already exists");
        }

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
                        new ResourceNotFoundException("Department not found with id: " + id));
    }

    public Department updateDepartment(Long id, Department department) {

        // "First, find the existing department using its ID."
        Department existingDepartment = getDepartmentById(id);

        // "Update the department name with the new value."
        existingDepartment.setName(department.getName());

        // "save() updates the existing department in the database."
        return departmentRepository.save(existingDepartment);
    }

    public void deleteDepartment(Long id) {

        // "Check whether the department exists before deleting it."
        Department department = getDepartmentById(id);

        // "delete() removes the department."
        departmentRepository.delete(department);
    }
}