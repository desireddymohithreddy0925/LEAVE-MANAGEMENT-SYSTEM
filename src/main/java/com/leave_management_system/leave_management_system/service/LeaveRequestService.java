package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.dto.OverlapCheckResponseDTO;
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

import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.leave_management_system.leave_management_system.entity.LeaveRequestSignoff;
import com.leave_management_system.leave_management_system.repository.LeaveRequestSignoffRepository;
import com.leave_management_system.leave_management_system.dto.LeaveSignoffResponseDTO;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@PreAuthorize("hasAuthority('LEAVE_VIEW_ALL')")
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final TransactionReviewService transactionReviewService;
    private final UserRepository userRepository;
    private final LeaveRequestSignoffRepository leaveRequestSignoffRepository;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            EmployeeRepository employeeRepository,
            LeaveTypeRepository leaveTypeRepository) {
        this(leaveRequestRepository, leaveBalanceRepository, employeeRepository, leaveTypeRepository, null, null, null);
    }

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            EmployeeRepository employeeRepository,
            LeaveTypeRepository leaveTypeRepository,
            TransactionReviewService transactionReviewService,
            UserRepository userRepository) {
        this(leaveRequestRepository, leaveBalanceRepository, employeeRepository, leaveTypeRepository, transactionReviewService, userRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
            LeaveBalanceRepository leaveBalanceRepository,
            EmployeeRepository employeeRepository,
            LeaveTypeRepository leaveTypeRepository,
            @Lazy TransactionReviewService transactionReviewService,
            @Lazy UserRepository userRepository,
            @Lazy LeaveRequestSignoffRepository leaveRequestSignoffRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.transactionReviewService = transactionReviewService;
        this.userRepository = userRepository;
        this.leaveRequestSignoffRepository = leaveRequestSignoffRepository;
    }

    private User resolveCurrentActor() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && userRepository != null) {
                return userRepository.findByEmail(auth.getName()).orElse(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String resolveCurrentActorEmail() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception ignored) {
        }
        return "system";
    }

    private boolean isCallerAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isCallerHr(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR"));
    }

    private boolean isCallerManager(Authentication auth, Employee applicant) {
        if (auth == null || applicant == null) return false;
        if (applicant.getManager() != null && applicant.getManager().getUser() != null) {
            if (applicant.getManager().getUser().getEmail().equals(auth.getName())) {
                return true;
            }
        }
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
    }

    private void recordSignoff(LeaveRequest leaveRequest, String role, User approver, String status, String comments) {
        if (leaveRequestSignoffRepository != null && approver != null) {
            LeaveRequestSignoff signoff = new LeaveRequestSignoff(leaveRequest, role, approver, status, comments);
            leaveRequestSignoffRepository.save(signoff);
            if (leaveRequest.getSignoffs() == null) {
                leaveRequest.setSignoffs(new java.util.ArrayList<>());
            }
            leaveRequest.getSignoffs().add(signoff);
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('LEAVE_CREATE')")
    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "LEAVE_APPLIED", entityType = "LeaveRequest")
    public LeaveResponseDTO createLeaveRequest(LeaveRequestDTO dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        if (dto.getStartDate().getYear() != dto.getEndDate().getYear()) {
            throw new IllegalArgumentException(
                    "Leave request cannot span across multiple calendar years. Please submit separate requests for each year.");
        }

        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new IllegalStateException("Employee is not active");
        }

        LeaveType leaveType = leaveTypeRepository.findById(dto.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        if (!leaveType.isActive()) {
            throw new IllegalArgumentException("This leave type is not active");
        }

        // Overlap detection: check if there's any pending or approved leave for these
        // dates.
        boolean hasOverlap = leaveRequestRepository.hasOverlappingLeave(
                employee,
                dto.getStartDate(),
                dto.getEndDate(),
                List.of(LeaveStatus.PENDING, LeaveStatus.PARTIALLY_APPROVED, LeaveStatus.APPROVED));
        if (hasOverlap) {
            throw new IllegalArgumentException("Leave request overlaps with an existing pending or approved leave");
        }

        long requestedDays = calculateWorkingDays(dto.getStartDate(), dto.getEndDate());
        if (requestedDays == 0) {
            throw new IllegalArgumentException("Requested leave duration contains only weekends");
        }

        int leaveYear = dto.getStartDate().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYearWithLock(employee, leaveType, leaveYear)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave balance not found for this employee and leave type for year " + leaveYear));

        if (balance.getAvailable() < requestedDays) {
            throw new InsufficientLeaveException("Insufficient leave balance. Available: " + balance.getAvailable()
                    + ", Requested: " + requestedDays);
        }

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setStartDate(dto.getStartDate());
        leaveRequest.setEndDate(dto.getEndDate());
        leaveRequest.setReason(dto.getReason());
        leaveRequest.setStatus(LeaveStatus.PENDING);
        if (dto.getRequiresDualApproval() != null) {
            leaveRequest.setRequiresDualApproval(dto.getRequiresDualApproval());
        }
        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        if (transactionReviewService != null) {
            transactionReviewService.recordLeaveRequest(saved, resolveCurrentActor(), resolveCurrentActorEmail());
        }
        return LeaveResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@securityService.canViewEmployee(authentication, #employeeId)")
    public OverlapCheckResponseDTO checkOverlap(Long employeeId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        List<LeaveRequest> overlappingRequests = leaveRequestRepository.findOverlappingLeaves(
                employee,
                startDate,
                endDate,
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED));

        boolean hasOverlap = !overlappingRequests.isEmpty();
        String message = hasOverlap
                ? "Leave request overlaps with " + overlappingRequests.size() + " existing pending or approved leave(s)"
                : "No overlapping leave requests found";

        List<LeaveResponseDTO> conflictingDtos = overlappingRequests.stream()
                .map(LeaveResponseDTO::fromEntity)
                .collect(Collectors.toList());

        return new OverlapCheckResponseDTO(hasOverlap, message, conflictingDtos);
    }

    public List<LeaveResponseDTO> getAllLeaveRequests() {
        return leaveRequestRepository.findAll().stream()
                .map(LeaveResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public Page<LeaveResponseDTO> searchLeaveRequests(Long employeeId, LeaveStatus status, LocalDate startDate,
            LocalDate endDate, Long leaveTypeId, Pageable pageable) {
        return searchLeaveRequests(employeeId, status, startDate, endDate, leaveTypeId, null, pageable);
    }

    public Page<LeaveResponseDTO> searchLeaveRequests(Long employeeId, LeaveStatus status, LocalDate startDate,
            LocalDate endDate, Long leaveTypeId, Long departmentId, Pageable pageable) {
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
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("employee").get("department").get("id"), departmentId));
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

    @PreAuthorize("@securityService.isSelf(authentication, #employeeId) or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_HR')")
    public Page<LeaveResponseDTO> getMyLeaveRequests(Long employeeId, LeaveStatus status, Integer year, Pageable pageable) {
        Specification<LeaveRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("employee").get("id"), employeeId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (year != null) {
                LocalDate startOfYear = LocalDate.of(year, 1, 1);
                LocalDate endOfYear = LocalDate.of(year, 12, 31);
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startOfYear));
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), endOfYear));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return leaveRequestRepository.findAll(spec, pageable).map(LeaveResponseDTO::fromEntity);
    }

    @PreAuthorize("@securityService.canViewLeaveRequest(authentication, #id)")
    public LeaveResponseDTO getLeaveRequestById(Long id) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        return LeaveResponseDTO.fromEntity(leaveRequest);
    }

    @PreAuthorize("@securityService.canViewEmployee(authentication, #employeeId)")
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

    @PreAuthorize("hasAuthority('LEAVE_VIEW_TEAM')")
    public List<LeaveResponseDTO> getTeamLeaveRequests(Long managerId) {
        return leaveRequestRepository.findTeamLeaveRequestsByManagerId(managerId).stream()
                .map(LeaveResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    private LeaveRequest findLeaveRequestWithLock(Long id) {
        return leaveRequestRepository.findByIdWithPessimisticLock(id)
                .or(() -> leaveRequestRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
    }

    @Transactional
    @PreAuthorize("@securityService.canApproveLeaveRequest(authentication, #id)")
    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "LEAVE_APPROVED", entityType = "LeaveRequest")
    public LeaveResponseDTO approveLeaveRequest(Long id) {
        LeaveRequest leaveRequest = findLeaveRequestWithLock(id);

        // Protect against double deduction by ensuring only PENDING or PARTIALLY_APPROVED requests can be approved.
        if (leaveRequest.getStatus() != LeaveStatus.PENDING && leaveRequest.getStatus() != LeaveStatus.PARTIALLY_APPROVED) {
            throw new IllegalArgumentException("Only pending requests can be approved");
        }

        if (leaveRequest.getStartDate().getYear() != leaveRequest.getEndDate().getYear()) {
            throw new IllegalArgumentException(
                    "Leave request cannot span across multiple calendar years. Please submit separate requests for each year.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User actor = resolveCurrentActor();
        boolean isAdmin = isCallerAdmin(auth);

        // If dual approval is NOT required OR caller is ADMIN (admin direct-approve override):
        if (!leaveRequest.isRequiresDualApproval() || isAdmin) {
            return finalizeFullApproval(leaveRequest, actor, "ADMIN");
        }

        boolean isHr = isCallerHr(auth);
        boolean isMgr = isCallerManager(auth, leaveRequest.getEmployee());

        String signoffRole;
        if (isMgr && !"APPROVED".equals(leaveRequest.getManagerApprovalStatus())) {
            signoffRole = "MANAGER";
        } else if (isHr && !"APPROVED".equals(leaveRequest.getHrApprovalStatus())) {
            signoffRole = "HR";
        } else if (isMgr) {
            signoffRole = "MANAGER";
        } else if (isHr) {
            signoffRole = "HR";
        } else {
            return finalizeFullApproval(leaveRequest, actor, "APPROVER");
        }

        if ("MANAGER".equals(signoffRole)) {
            if ("APPROVED".equals(leaveRequest.getManagerApprovalStatus())) {
                throw new IllegalArgumentException("Manager has already approved this leave request");
            }
            leaveRequest.setManagerApprovalStatus("APPROVED");
            leaveRequest.setManagerApprovedBy(actor);
            leaveRequest.setManagerApprovedAt(LocalDateTime.now());
            recordSignoff(leaveRequest, "MANAGER", actor, "APPROVED", "Approved by Manager");
        } else if ("HR".equals(signoffRole)) {
            if ("APPROVED".equals(leaveRequest.getHrApprovalStatus())) {
                throw new IllegalArgumentException("HR has already approved this leave request");
            }
            leaveRequest.setHrApprovalStatus("APPROVED");
            leaveRequest.setHrApprovedBy(actor);
            leaveRequest.setHrApprovedAt(LocalDateTime.now());
            recordSignoff(leaveRequest, "HR", actor, "APPROVED", "Approved by HR");
        }

        boolean managerApproved = "APPROVED".equals(leaveRequest.getManagerApprovalStatus());
        boolean hrApproved = "APPROVED".equals(leaveRequest.getHrApprovalStatus());

        if (managerApproved && hrApproved) {
            return finalizeFullApproval(leaveRequest, actor, signoffRole);
        } else {
            leaveRequest.setStatus(LeaveStatus.PARTIALLY_APPROVED);
            LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
            return LeaveResponseDTO.fromEntity(saved);
        }
    }

    private LeaveResponseDTO finalizeFullApproval(LeaveRequest leaveRequest, User actor, String approverRole) {
        // Ensure no other APPROVED leave request overlaps with this one (excluding itself)
        boolean hasApprovedOverlap = leaveRequestRepository.hasOverlappingLeaveExcludingId(
                leaveRequest.getEmployee(),
                leaveRequest.getId(),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                List.of(LeaveStatus.APPROVED));
        if (hasApprovedOverlap) {
            throw new IllegalArgumentException(
                    "Cannot approve: an overlapping leave request is already approved for this employee");
        }

        long requestedDays = calculateWorkingDays(leaveRequest.getStartDate(), leaveRequest.getEndDate());

        if (leaveRequest.getEmployee() != null && leaveRequest.getEmployee().getId() != null) {
            employeeRepository.findByIdWithPessimisticLock(leaveRequest.getEmployee().getId());
        }

        int leaveYear = leaveRequest.getStartDate().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYearWithLock(leaveRequest.getEmployee(), leaveRequest.getLeaveType(), leaveYear)
                .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found for year " + leaveYear));

        // Re-check overlap after acquiring employee and balance locks to prevent concurrent approval race condition
        if (leaveRequestRepository.hasOverlappingLeaveExcludingId(
                leaveRequest.getEmployee(),
                leaveRequest.getId(),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                List.of(LeaveStatus.APPROVED))) {
            throw new IllegalArgumentException(
                    "Cannot approve: an overlapping leave request is already approved for this employee");
        }

        if (balance.getAvailable() < requestedDays) {
            throw new InsufficientLeaveException("Insufficient leave balance");
        }

        balance.setAvailable(balance.getAvailable() - (int) requestedDays);
        int currentUsed = balance.getUsed() != null ? balance.getUsed() : 0;
        balance.setUsed(currentUsed + (int) requestedDays);
        leaveBalanceRepository.save(balance);

        leaveRequest.setStatus(LeaveStatus.APPROVED);
        if (leaveRequest.isRequiresDualApproval() && actor != null && "ADMIN".equals(approverRole)) {
            if (!"APPROVED".equals(leaveRequest.getManagerApprovalStatus())) {
                leaveRequest.setManagerApprovalStatus("APPROVED");
                leaveRequest.setManagerApprovedBy(actor);
                leaveRequest.setManagerApprovedAt(LocalDateTime.now());
            }
            if (!"APPROVED".equals(leaveRequest.getHrApprovalStatus())) {
                leaveRequest.setHrApprovalStatus("APPROVED");
                leaveRequest.setHrApprovedBy(actor);
                leaveRequest.setHrApprovedAt(LocalDateTime.now());
            }
            recordSignoff(leaveRequest, "ADMIN", actor, "APPROVED", "Approved by Admin override");
        }

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        if (transactionReviewService != null) {
            transactionReviewService.recordLeaveApproval(saved, resolveCurrentActor(), resolveCurrentActorEmail());
        }
        return LeaveResponseDTO.fromEntity(saved);
    }

    @Transactional
    @PreAuthorize("@securityService.canRejectLeaveRequest(authentication, #id)")
    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "LEAVE_REJECTED", entityType = "LeaveRequest")
    public LeaveResponseDTO rejectLeaveRequest(Long id, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        if (rejectionReason.length() > 500) {
            throw new IllegalArgumentException("Rejection reason must not exceed 500 characters");
        }

        LeaveRequest leaveRequest = findLeaveRequestWithLock(id);

        if (leaveRequest.getStatus() != LeaveStatus.PENDING && leaveRequest.getStatus() != LeaveStatus.PARTIALLY_APPROVED) {
            throw new IllegalArgumentException("Only pending requests can be rejected");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User actor = resolveCurrentActor();
        String role = isCallerAdmin(auth) ? "ADMIN" : (isCallerHr(auth) ? "HR" : "MANAGER");

        if (leaveRequest.isRequiresDualApproval()) {
            if ("HR".equals(role)) {
                leaveRequest.setHrApprovalStatus("REJECTED");
            } else if ("MANAGER".equals(role)) {
                leaveRequest.setManagerApprovalStatus("REJECTED");
            }
            recordSignoff(leaveRequest, role, actor, "REJECTED", rejectionReason.trim());
        }

        leaveRequest.setStatus(LeaveStatus.REJECTED);
        leaveRequest.setRejectionReason(rejectionReason.trim());
        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        if (transactionReviewService != null) {
            transactionReviewService.recordLeaveRejection(saved, rejectionReason.trim(), resolveCurrentActor(), resolveCurrentActorEmail());
        }
        return LeaveResponseDTO.fromEntity(saved);
    }

    @Transactional
    @PreAuthorize("@securityService.canCancelLeaveRequest(authentication, #id)")
    @com.leave_management_system.leave_management_system.aspect.AuditAction(action = "LEAVE_CANCELLED", entityType = "LeaveRequest")
    public LeaveResponseDTO cancelLeaveRequest(Long id) {
        LeaveRequest leaveRequest = findLeaveRequestWithLock(id);

        if (leaveRequest.getStatus() != LeaveStatus.PENDING 
                && leaveRequest.getStatus() != LeaveStatus.PARTIALLY_APPROVED 
                && leaveRequest.getStatus() != LeaveStatus.APPROVED) {
            if (leaveRequest.getStatus() == LeaveStatus.REJECTED) {
                throw new IllegalArgumentException("Cannot cancel a rejected leave request");
            }
            if (leaveRequest.getStatus() == LeaveStatus.CANCELLED) {
                throw new IllegalArgumentException("Leave request is already cancelled");
            }
        }

        boolean wasApproved = leaveRequest.getStatus() == LeaveStatus.APPROVED;
        if (wasApproved) {
            if (leaveRequest.getStartDate().getYear() != leaveRequest.getEndDate().getYear()) {
                throw new IllegalArgumentException(
                        "Leave request cannot span across multiple calendar years. Please submit separate requests for each year.");
            }
            long requestedDays = calculateWorkingDays(leaveRequest.getStartDate(), leaveRequest.getEndDate());
            int leaveYear = leaveRequest.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeAndLeaveTypeAndYearWithLock(leaveRequest.getEmployee(), leaveRequest.getLeaveType(), leaveYear)
                    .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found for year " + leaveYear));

            balance.setAvailable(balance.getAvailable() + (int) requestedDays);
            int currentUsed = balance.getUsed() != null ? balance.getUsed() : 0;
            balance.setUsed(Math.max(0, currentUsed - (int) requestedDays));
            leaveBalanceRepository.save(balance);
        }

        leaveRequest.setStatus(LeaveStatus.CANCELLED);
        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        if (transactionReviewService != null) {
            transactionReviewService.recordLeaveCancellation(saved, wasApproved, resolveCurrentActor(), resolveCurrentActorEmail());
        }
        return LeaveResponseDTO.fromEntity(saved);
    }

    @PreAuthorize("@securityService.canViewLeaveRequest(authentication, #id)")
    public java.util.List<LeaveSignoffResponseDTO> getLeaveRequestSignoffs(Long id) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        if (leaveRequestSignoffRepository != null) {
            return leaveRequestSignoffRepository.findByLeaveRequestId(id).stream()
                    .map(LeaveSignoffResponseDTO::fromEntity)
                    .collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }

    public void deleteLeaveRequest(Long id) {
        LeaveRequest existing = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));

        leaveRequestRepository.delete(existing);
    }

    // Helper method to calculate working days between two dates, excluding
    // weekends (Saturdays and Sundays).
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