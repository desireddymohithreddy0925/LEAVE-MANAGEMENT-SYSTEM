package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository
        extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByEmployeeAndLeaveType(
            Employee employee,
            LeaveType leaveType
    );

    List<LeaveBalance> findByEmployee(Employee employee);

    List<LeaveBalance> findByEmployeeAndYear(Employee employee, Integer year);

    Optional<LeaveBalance> findByEmployeeAndLeaveTypeAndYear(
            Employee employee,
            LeaveType leaveType,
            Integer year
    );

    boolean existsByEmployeeAndLeaveType(
            Employee employee,
            LeaveType leaveType
    );

    boolean existsByEmployeeAndLeaveTypeAndYear(
            Employee employee,
            LeaveType leaveType,
            Integer year
    );

    boolean existsByLeaveType(LeaveType leaveType);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.employee = :employee AND lb.leaveType = :leaveType")
    Optional<LeaveBalance> findByEmployeeAndLeaveTypeWithLock(
            @Param("employee") Employee employee,
            @Param("leaveType") LeaveType leaveType
    );

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.employee = :employee AND lb.leaveType = :leaveType AND lb.year = :year")
    Optional<LeaveBalance> findByEmployeeAndLeaveTypeAndYearWithLock(
            @Param("employee") Employee employee,
            @Param("leaveType") LeaveType leaveType,
            @Param("year") Integer year
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.id = :id")
    Optional<LeaveBalance> findByIdWithPessimisticLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.employee = :employee AND lb.leaveType = :leaveType AND lb.year = :year")
    Optional<LeaveBalance> findByEmployeeAndLeaveTypeAndYearWithPessimisticLock(
            @Param("employee") Employee employee,
            @Param("leaveType") LeaveType leaveType,
            @Param("year") Integer year
    );
}
