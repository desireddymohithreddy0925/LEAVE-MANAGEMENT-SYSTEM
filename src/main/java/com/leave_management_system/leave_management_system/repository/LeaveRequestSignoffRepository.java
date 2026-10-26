package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.LeaveRequestSignoff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveRequestSignoffRepository extends JpaRepository<LeaveRequestSignoff, Long> {

    List<LeaveRequestSignoff> findByLeaveRequestId(Long leaveRequestId);

    Optional<LeaveRequestSignoff> findByLeaveRequestIdAndApproverRole(Long leaveRequestId, String approverRole);

    void deleteByLeaveRequestId(Long leaveRequestId);
}
