package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import com.leave_management_system.leave_management_system.entity.Employee;
import java.util.List;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployee(Employee employee);
    List<LeaveRequest> findByStatus(LeaveStatus status);
}