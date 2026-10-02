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

    boolean existsByEmployeeAndLeaveType(
            Employee employee,
            LeaveType leaveType
    );

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.employee = :employee AND lb.leaveType = :leaveType")
    Optional<LeaveBalance> findByEmployeeAndLeaveTypeWithLock(
            @Param("employee") Employee employee,
            @Param("leaveType") LeaveType leaveType
    );
}
