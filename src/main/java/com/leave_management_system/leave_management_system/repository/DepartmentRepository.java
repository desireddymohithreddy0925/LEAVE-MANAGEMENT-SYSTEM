package com.leave_management_system.leave_management_system.repository;

// JpaRepository provides built-in CRUD operations such as save(), findAll(),

// findById(), existsById(), and deleteById().

import com.leave_management_system.leave_management_system.entity.Department;


import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;



public interface DepartmentRepository extends JpaRepository<Department, Long> {


    Optional<Department> findByName(String name);

    boolean existsByName(String name);
}