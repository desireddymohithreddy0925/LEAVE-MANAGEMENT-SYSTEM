package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
public class LeaveRequestRepositoryTest {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setEmployeeCode("EMP-001");
        employee.setStatus("ACTIVE");
        employee.setSalary(new java.math.BigDecimal("50000"));
        employee = employeeRepository.save(employee);

        LeaveType leaveType = new LeaveType("Annual" + java.util.UUID.randomUUID().toString().substring(0, 8), "", 20);
        leaveType.setActive(true);
        leaveType = leaveTypeRepository.save(leaveType);

        LeaveRequest existingLeave = new LeaveRequest();
        existingLeave.setEmployee(employee);
        existingLeave.setLeaveType(leaveType);
        // Existing leave: 10th to 15th
        existingLeave.setStartDate(LocalDate.of(2026, 9, 10));
        existingLeave.setEndDate(LocalDate.of(2026, 9, 15));
        existingLeave.setStatus(LeaveStatus.PENDING);
        leaveRequestRepository.save(existingLeave);
    }

    private boolean checkOverlap(int startDay, int endDay) {
        return leaveRequestRepository.hasOverlappingLeave(
                employee,
                LocalDate.of(2026, 9, startDay),
                LocalDate.of(2026, 9, endDay),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
        );
    }

    @Test
    void testOverlapEdgeCases() {
        // Existing leave is 10 -> 15

        // 12 -> 14 (Fully inside)
        assertTrue(checkOverlap(12, 14));

        // 8 -> 11 (Overlaps beginning)
        assertTrue(checkOverlap(8, 11));

        // 14 -> 18 (Overlaps end)
        assertTrue(checkOverlap(14, 18));

        // 8 -> 18 (Fully encompasses)
        assertTrue(checkOverlap(8, 18));

        // 10 -> 15 (Exact match)
        assertTrue(checkOverlap(10, 15));

        // 10 -> 10 (Single day at start)
        assertTrue(checkOverlap(10, 10));

        // 15 -> 15 (Single day at end)
        assertTrue(checkOverlap(15, 15));

        // 16 -> 18 (No overlap, completely after)
        assertFalse(checkOverlap(16, 18));
        
        // 8 -> 9 (No overlap, completely before)
        assertFalse(checkOverlap(8, 9));
    }

    @Test
    void testOverlapIgnoredForRejectedOrCancelled() {
        // Change existing leave to REJECTED
        LeaveRequest existing = leaveRequestRepository.findByEmployee(employee).get(0);
        existing.setStatus(LeaveStatus.REJECTED);
        leaveRequestRepository.save(existing);

        // Now an overlapping request should NOT return true because it filters by PENDING/APPROVED
        assertFalse(checkOverlap(12, 14));

        // Change to CANCELLED
        existing.setStatus(LeaveStatus.CANCELLED);
        leaveRequestRepository.save(existing);
        assertFalse(checkOverlap(12, 14));
    }

    @Test
    void testFindOverlappingLeaves_ReturnsMatchingEntities() {
        List<LeaveRequest> overlaps = leaveRequestRepository.findOverlappingLeaves(
                employee,
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 14),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED));

        assertEquals(1, overlaps.size());
        assertEquals(LocalDate.of(2026, 9, 10), overlaps.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 9, 15), overlaps.get(0).getEndDate());
        assertEquals(LeaveStatus.PENDING, overlaps.get(0).getStatus());
    }

    @Test
    void testFindOverlappingLeavesExcludingId() {
        LeaveRequest existing = leaveRequestRepository.findByEmployee(employee).get(0);

        // Exclude the existing ID -> should find 0 overlaps
        List<LeaveRequest> excluded = leaveRequestRepository.findOverlappingLeavesExcludingId(
                employee,
                existing.getId(),
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 14),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED));
        assertTrue(excluded.isEmpty());

        boolean hasOverlapExcludingSelf = leaveRequestRepository.hasOverlappingLeaveExcludingId(
                employee,
                existing.getId(),
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 14),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED));
        assertFalse(hasOverlapExcludingSelf);

        // Exclude dummy ID (e.g. 999999L) -> should find the existing overlap
        List<LeaveRequest> notExcluded = leaveRequestRepository.findOverlappingLeavesExcludingId(
                employee,
                999999L,
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 14),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED));
        assertEquals(1, notExcluded.size());
    }

    @Test
    void testOverlap_DifferentEmployees_NoOverlap() {
        Employee otherEmployee = new Employee();
        otherEmployee.setFirstName("Jane");
        otherEmployee.setLastName("Smith");
        otherEmployee.setEmployeeCode("EMP-002");
        otherEmployee.setStatus("ACTIVE");
        otherEmployee.setSalary(new java.math.BigDecimal("60000"));
        otherEmployee = employeeRepository.save(otherEmployee);

        boolean otherEmployeeOverlap = leaveRequestRepository.hasOverlappingLeave(
                otherEmployee,
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 15),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED));
        assertFalse(otherEmployeeOverlap);
    }
}
