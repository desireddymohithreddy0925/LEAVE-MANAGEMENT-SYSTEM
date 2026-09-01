package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.LeaveBalanceRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.exception.InsufficientLeaveException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
                               LeaveBalanceRepository leaveBalanceRepository,
                               EmployeeRepository employeeRepository,
                               LeaveTypeRepository leaveTypeRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Transactional
    public LeaveResponseDTO createLeaveRequest(LeaveRequestDTO dto) {
        if (dto.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Start date cannot be in the past");
        }

        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        if (!employee.isActive()) {
            throw new IllegalArgumentException("Inactive employees cannot apply for leave");
        }

        LeaveType leaveType = leaveTypeRepository.findById(dto.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        // "Check if leave type is active."
        if (!leaveType.isActive()) {
            throw new IllegalArgumentException("This leave type is not active");
        }

        // "Overlap detection: check if there's any pending or approved leave for these dates."
        boolean hasOverlap = leaveRequestRepository.hasOverlappingLeave(
                employee,
                dto.getStartDate(),
                dto.getEndDate(),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
        );
        if (hasOverlap) {
            throw new IllegalArgumentException("Leave request overlaps with an existing pending or approved leave");
        }

        long requestedDays = calculateWorkingDays(dto.getStartDate(), dto.getEndDate());
        // "Ensure the requested duration contains at least one working day."
        if (requestedDays == 0) {
            throw new IllegalArgumentException("Requested leave duration contains only weekends");
        }

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeAndLeaveType(employee, leaveType)
                .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found for this employee and leave type"));

        if (balance.getAvailableDays() < requestedDays) {
            throw new InsufficientLeaveException("Insufficient leave balance. Available: " + balance.getAvailableDays() + ", Requested: " + requestedDays);
        }

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setStartDate(dto.getStartDate());
        leaveRequest.setEndDate(dto.getEndDate());
        leaveRequest.setReason(dto.getReason());
        leaveRequest.setStatus(LeaveStatus.PENDING);

        return LeaveResponseDTO.fromEntity(leaveRequestRepository.save(leaveRequest));
    }

    public List<LeaveResponseDTO> getAllLeaveRequests() {
        return leaveRequestRepository.findAll().stream()
                .map(LeaveResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public Page<LeaveResponseDTO> searchLeaveRequests(Long employeeId, LeaveStatus status, LocalDate startDate, LocalDate endDate, Long leaveTypeId, Pageable pageable) {
        Specification<LeaveRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employee").get("id"), employeeId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (leaveTypeId != null) {
                predicates.add(cb.equal(root.get("leaveType").get("id"), leaveTypeId));
            }
            if (startDate != null && endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), endDate));
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), startDate));
            } else if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startDate));
            } else if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), endDate));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        return leaveRequestRepository.findAll(spec, pageable).map(LeaveResponseDTO::fromEntity);
    }

    public LeaveResponseDTO getLeaveRequestById(Long id) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        return LeaveResponseDTO.fromEntity(leaveRequest);
    }

    public List<LeaveResponseDTO> getLeaveRequestsByEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        return leaveRequestRepository.findByEmployee(employee).stream()
                .map(LeaveResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<LeaveResponseDTO> getLeaveRequestsByStatus(LeaveStatus status) {
        return leaveRequestRepository.findByStatus(status).stream()
                .map(LeaveResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeaveResponseDTO approveLeaveRequest(Long id) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        // "Protect against double deduction by ensuring only PENDING requests can be approved."
        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be approved");
        }

        long requestedDays = calculateWorkingDays(leaveRequest.getStartDate(), leaveRequest.getEndDate());

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeAndLeaveType(leaveRequest.getEmployee(), leaveRequest.getLeaveType())
                .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found"));

        if (balance.getAvailableDays() < requestedDays) {
            throw new InsufficientLeaveException("Insufficient leave balance");
        }

        balance.setAvailableDays(balance.getAvailableDays() - (int) requestedDays);
        leaveBalanceRepository.save(balance);

        leaveRequest.setStatus(LeaveStatus.APPROVED);
        return LeaveResponseDTO.fromEntity(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional
    public LeaveResponseDTO rejectLeaveRequest(Long id, String rejectionReason) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be rejected");
        }

        leaveRequest.setStatus(LeaveStatus.REJECTED);
        leaveRequest.setRejectionReason(rejectionReason);
        return LeaveResponseDTO.fromEntity(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional
    public LeaveResponseDTO cancelLeaveRequest(Long id) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        if (leaveRequest.getStatus() == LeaveStatus.REJECTED) {
            throw new IllegalArgumentException("Cannot cancel a rejected leave request");
        }
        if (leaveRequest.getStatus() == LeaveStatus.CANCELLED) {
            throw new IllegalArgumentException("Leave request is already cancelled");
        }

        // "If the leave was already approved, we must restore the deducted days."
        if (leaveRequest.getStatus() == LeaveStatus.APPROVED) {
            long requestedDays = calculateWorkingDays(leaveRequest.getStartDate(), leaveRequest.getEndDate());
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeAndLeaveType(leaveRequest.getEmployee(), leaveRequest.getLeaveType())
                    .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found"));

            balance.setAvailableDays(balance.getAvailableDays() + (int) requestedDays);
            leaveBalanceRepository.save(balance);
        }

        leaveRequest.setStatus(LeaveStatus.CANCELLED);
        return LeaveResponseDTO.fromEntity(leaveRequestRepository.save(leaveRequest));
    }

    public void deleteLeaveRequest(Long id) {
        LeaveRequest existing = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        leaveRequestRepository.delete(existing);
    }

    // "Helper method to calculate working days between two dates, excluding weekends (Saturdays and Sundays)."
    private long calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        long workingDays = 0;
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            java.time.DayOfWeek day = date.getDayOfWeek();
            if (day != java.time.DayOfWeek.SATURDAY && day != java.time.DayOfWeek.SUNDAY) {
                workingDays++;
            }
            date = date.plusDays(1);
        }
        return workingDays;
    }
}