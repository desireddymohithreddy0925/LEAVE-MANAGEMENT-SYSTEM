package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.leave_management_system.leave_management_system.entity.Employee;
import java.util.List;
import java.time.LocalDate;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.entity.LeaveType;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>, JpaSpecificationExecutor<LeaveRequest> {
    List<LeaveRequest> findByEmployee(Employee employee);
    List<LeaveRequest> findByStatus(LeaveStatus status);
    List<LeaveRequest> findByEmployee_Manager_Id(Long managerId);
    boolean existsByLeaveType(LeaveType leaveType);
    boolean existsByEmployeeAndLeaveTypeAndStatusIn(Employee employee, LeaveType leaveType, List<LeaveStatus> statuses);

    @Query("SELECT lr FROM LeaveRequest lr WHERE " +
           "(lr.employee.manager.id = :managerId OR lr.employee.department.manager.id = :managerId) " +
           "AND lr.employee.id != :managerId")
    List<LeaveRequest> findTeamLeaveRequestsByManagerId(@Param("managerId") Long managerId);

    @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr WHERE lr.employee = :employee " +
           "AND lr.status IN (:statuses) " +
           "AND lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate")
    boolean hasOverlappingLeave(@Param("employee") Employee employee,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate,
                                @Param("statuses") List<LeaveStatus> statuses);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee = :employee " +
           "AND lr.status IN (:statuses) " +
           "AND lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate " +
           "ORDER BY lr.startDate ASC")
    List<LeaveRequest> findOverlappingLeaves(@Param("employee") Employee employee,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate,
                                            @Param("statuses") List<LeaveStatus> statuses);

    @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr WHERE lr.employee = :employee " +
           "AND lr.id != :excludeId " +
           "AND lr.status IN (:statuses) " +
           "AND lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate")
    boolean hasOverlappingLeaveExcludingId(@Param("employee") Employee employee,
                                          @Param("excludeId") Long excludeId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate,
                                          @Param("statuses") List<LeaveStatus> statuses);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee = :employee " +
           "AND lr.id != :excludeId " +
           "AND lr.status IN (:statuses) " +
           "AND lr.startDate <= :endDate " +
           "AND lr.endDate >= :startDate " +
           "ORDER BY lr.startDate ASC")
    List<LeaveRequest> findOverlappingLeavesExcludingId(@Param("employee") Employee employee,
                                                       @Param("excludeId") Long excludeId,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate,
                                                       @Param("statuses") List<LeaveStatus> statuses);
}