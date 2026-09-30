package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUserEmail(String email);

    boolean existsByUserEmail(String email);

    @Query("SELECT e FROM Employee e WHERE LOWER(e.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(e.user.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Employee> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    List<Employee> findByDepartmentId(Long departmentId);

    boolean existsByDepartmentId(Long departmentId);

    @Query("SELECT e FROM Employee e WHERE (e.manager.id = :managerId OR e.department.manager.id = :managerId) AND e.id != :managerId")
    List<Employee> findTeamByManagerId(@Param("managerId") Long managerId);
}