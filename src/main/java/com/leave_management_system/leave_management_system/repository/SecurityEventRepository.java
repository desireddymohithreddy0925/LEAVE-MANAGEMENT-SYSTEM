package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.SecurityEvent;
import com.leave_management_system.leave_management_system.entity.SecurityEventSeverity;
import com.leave_management_system.leave_management_system.entity.SecurityEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long>, JpaSpecificationExecutor<SecurityEvent> {

    List<SecurityEvent> findByEventType(String eventType);

    long countByStatus(SecurityEventStatus status);

    long countBySeverity(SecurityEventSeverity severity);

    long countByStatusAndSeverityIn(SecurityEventStatus status, Collection<SecurityEventSeverity> severities);

    long countByEventTypeAndEmailAndCreatedAtAfter(String eventType, String email, LocalDateTime after);

    long countByEventTypeAndIpAddressAndCreatedAtAfter(String eventType, String ipAddress, LocalDateTime after);

    long countByEventTypeInAndIpAddressAndCreatedAtAfter(Collection<String> eventTypes, String ipAddress, LocalDateTime after);

    long countByEventTypeInAndEmailAndCreatedAtAfter(Collection<String> eventTypes, String email, LocalDateTime after);
}
