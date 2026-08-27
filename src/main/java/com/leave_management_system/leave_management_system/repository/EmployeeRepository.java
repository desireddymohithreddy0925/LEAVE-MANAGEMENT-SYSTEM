package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
// "Tells Spring that this interface belongs to the database/repository layer."

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);
    // "Searches for an employee using their email address."

    boolean existsByEmail(String email);
    // "Checks whether an employee with the given email already exists."
}