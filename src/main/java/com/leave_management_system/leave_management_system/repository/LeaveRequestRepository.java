package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.leave_management_system.leave_management_system.entity.Employee;
import java.util.List;
import java.time.LocalDate;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployee(Employee employee);
    List<LeaveRequest> findByStatus(LeaveStatus status);

    // "Custom query to find any overlapping leaves for an employee that are PENDING or APPROVED."
    @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr WHERE lr.employee = :employee " +
           "AND lr.status IN (:statuses) " +
           "AND lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate")
    boolean hasOverlappingLeave(@Param("employee") Employee employee,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate,
                                @Param("statuses") List<LeaveStatus> statuses);
}