package com.leave_management_system.leave_management_system.repository;

// "JpaRepository provides built-in CRUD operations such as save(), findAll(),
// findById(), existsById(), and deleteById()."


import com.leave_management_system.leave_management_system.entity.Department;

// "Imports the Department entity that this repository will manage."

import org.springframework.data.jpa.repository.JpaRepository;

// "Imports JpaRepository, which provides built-in CRUD database operations."


// "Creates a Spring Data JPA repository for the Department entity."
// "Long represents the data type of the Department primary key."


public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // "No custom methods are required yet because JpaRepository already provides:"
    // "save(), findAll(), findById(), existsById(), deleteById(), etc."
}