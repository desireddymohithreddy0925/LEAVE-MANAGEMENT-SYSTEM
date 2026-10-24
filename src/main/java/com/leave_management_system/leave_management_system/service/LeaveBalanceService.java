package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.aspect.AuditAction;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceResponseDTO;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveBalanceRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasAuthority('LEAVE_BALANCE_MANAGE')")
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TransactionReviewService transactionReviewService;
    private final UserRepository userRepository;

    public LeaveBalanceService(
            LeaveBalanceRepository leaveBalanceRepository,
            EmployeeRepository employeeRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeaveRequestRepository leaveRequestRepository) {
        this(leaveBalanceRepository, employeeRepository, leaveTypeRepository, leaveRequestRepository, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LeaveBalanceService(
            LeaveBalanceRepository leaveBalanceRepository,
            EmployeeRepository employeeRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeaveRequestRepository leaveRequestRepository,
            @Lazy TransactionReviewService transactionReviewService,
            @Lazy UserRepository userRepository) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.transactionReviewService = transactionReviewService;
        this.userRepository = userRepository;
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

    @AuditAction(action = "BALANCE_CREATED", entityType = "LeaveBalance")
    public LeaveBalanceResponseDTO createLeaveBalance(LeaveBalanceRequestDTO dto) {
        if (dto.getEmployeeId() == null) {
            throw new IllegalArgumentException("Employee ID is required");
        }
        if (dto.getLeaveTypeId() == null) {
            throw new IllegalArgumentException("Leave Type ID is required");
        }

        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + dto.getEmployeeId()));

        LeaveType leaveType = leaveTypeRepository.findById(dto.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave type not found with id: " + dto.getLeaveTypeId()));

        if (!leaveType.isActive()) {
            throw new IllegalArgumentException("Cannot create leave balance for inactive leave type");
        }

        int year = dto.getYear() != null ? dto.getYear() : Year.now().getValue();

        if (leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, year)) {
            throw new DuplicateResourceException(
                    "Leave balance already exists for this employee and leave type");
        }

        if (dto.getTotal() != null && dto.getTotal() < 0) {
            throw new IllegalArgumentException("Total days cannot be negative");
        }
        if (dto.getAvailable() != null && dto.getAvailable() < 0) {
            throw new IllegalArgumentException("Available days cannot be negative");
        }

        int initialDays;
        if (dto.getTotal() != null && dto.getAvailable() != null) {
            if (dto.getAvailable() > dto.getTotal()) {
                throw new IllegalArgumentException("Available days cannot exceed total days");
            }
            if (!dto.getTotal().equals(dto.getAvailable())) {
                throw new IllegalArgumentException(
                        "Initial available days (" + dto.getAvailable() + ") must equal total days (" + dto.getTotal()
                                + ") on balance creation");
            }
            initialDays = dto.getTotal();
        } else if (dto.getTotal() != null) {
            initialDays = dto.getTotal();
        } else if (dto.getAvailable() != null) {
            initialDays = dto.getAvailable();
        } else {
            initialDays = leaveType.getDefaultDays();
        }

        LeaveBalance leaveBalance = new LeaveBalance();
        leaveBalance.setEmployee(employee);
        leaveBalance.setLeaveType(leaveType);
        leaveBalance.setYear(year);
        leaveBalance.setTotal(initialDays);
        leaveBalance.setUsed(0);
        leaveBalance.setAvailable(initialDays);

        LeaveBalance saved = leaveBalanceRepository.save(leaveBalance);
        if (transactionReviewService != null) {
            transactionReviewService.recordBalanceCreation(saved, resolveCurrentActor(), resolveCurrentActorEmail());
        }
        return LeaveBalanceResponseDTO.fromEntity(saved);
    }

    public List<LeaveBalanceResponseDTO> getAllLeaveBalances() {
        return leaveBalanceRepository.findAll().stream()
                .map(LeaveBalanceResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public LeaveBalanceResponseDTO getLeaveBalanceById(Long id) {
        LeaveBalance balance = leaveBalanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave balance not found with id: " + id));
        return LeaveBalanceResponseDTO.fromEntity(balance);
    }

    public LeaveBalance getLeaveBalanceEntityById(Long id) {
        return leaveBalanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave balance not found with id: " + id));
    }

    @PreAuthorize("@securityService.canViewEmployee(authentication, #employeeId)")
    public List<LeaveBalanceResponseDTO> getBalancesByEmployee(Long employeeId) {
        return getBalancesByEmployee(employeeId, null);
    }

    @PreAuthorize("@securityService.canViewEmployee(authentication, #employeeId)")
    public List<LeaveBalanceResponseDTO> getBalancesByEmployee(Long employeeId, Integer year) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + employeeId));

        List<LeaveBalance> balances = (year != null)
                ? leaveBalanceRepository.findByEmployeeAndYear(employee, year)
                : leaveBalanceRepository.findByEmployee(employee);

        return balances.stream()
                .map(LeaveBalanceResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    @AuditAction(action = "BALANCE_INITIALIZED", entityType = "LeaveBalance")
    public List<LeaveBalanceResponseDTO> initializeBalancesForEmployee(Long employeeId, Integer year) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with id: " + employeeId));

        int targetYear = (year != null) ? year : Year.now().getValue();
        List<LeaveType> activeTypes = leaveTypeRepository.findByActive(true);
        List<LeaveBalance> createdBalances = new ArrayList<>();

        for (LeaveType leaveType : activeTypes) {
            boolean exists = leaveBalanceRepository.existsByEmployeeAndLeaveTypeAndYear(employee, leaveType, targetYear);
            if (!exists) {
                LeaveBalance balance = new LeaveBalance();
                balance.setEmployee(employee);
                balance.setLeaveType(leaveType);
                balance.setYear(targetYear);
                balance.setTotal(leaveType.getDefaultDays());
                balance.setUsed(0);
                balance.setAvailable(leaveType.getDefaultDays());
                createdBalances.add(leaveBalanceRepository.save(balance));
            }
        }

        return leaveBalanceRepository.findByEmployeeAndYear(employee, targetYear).stream()
                .map(LeaveBalanceResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    @AuditAction(action = "BALANCE_UPDATED", entityType = "LeaveBalance")
    public LeaveBalanceResponseDTO updateLeaveBalance(Long id, LeaveBalanceRequestDTO dto) {
        LeaveBalance leaveBalance = leaveBalanceRepository.findByIdWithPessimisticLock(id)
                .or(() -> leaveBalanceRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave balance not found with id: " + id));

        if (dto.getAvailable() == null && dto.getTotal() == null) {
            throw new IllegalArgumentException("At least one of total or available days must be provided for update");
        }

        int currentUsed = leaveBalance.getUsed() != null ? leaveBalance.getUsed() : 0;
        int targetAvailable = dto.getAvailable() != null ? dto.getAvailable() : leaveBalance.getAvailable();
        int targetTotal = dto.getTotal() != null ? dto.getTotal() : leaveBalance.getTotal();

        if (targetAvailable < 0) {
            throw new IllegalArgumentException("Available days cannot be negative");
        }
        if (targetTotal < 0) {
            throw new IllegalArgumentException("Total days cannot be negative");
        }
        if (targetTotal < targetAvailable) {
            throw new IllegalArgumentException("Total days cannot be less than available days");
        }
        if (currentUsed + targetAvailable > targetTotal) {
            throw new IllegalArgumentException(
                    "Used days (" + currentUsed + ") plus available days (" + targetAvailable
                            + ") cannot exceed total days (" + targetTotal + ")");
        }

        int oldTotal = leaveBalance.getTotal();
        int oldAvailable = leaveBalance.getAvailable();

        leaveBalance.setAvailable(targetAvailable);
        leaveBalance.setTotal(targetTotal);

        LeaveBalance saved = leaveBalanceRepository.save(leaveBalance);
        if (transactionReviewService != null) {
            transactionReviewService.recordBalanceAdjustment(saved, oldTotal, oldAvailable, resolveCurrentActor(), resolveCurrentActorEmail());
        }
        return LeaveBalanceResponseDTO.fromEntity(saved);
    }

    @Transactional
    @AuditAction(action = "BALANCE_DELETED", entityType = "LeaveBalance")
    public void deleteLeaveBalance(Long id) {
        LeaveBalance leaveBalance = leaveBalanceRepository.findByIdWithPessimisticLock(id)
                .or(() -> leaveBalanceRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave balance not found with id: " + id));

        if (leaveRequestRepository.existsByEmployeeAndLeaveTypeAndStatusIn(
                leaveBalance.getEmployee(),
                leaveBalance.getLeaveType(),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED))) {
            throw new IllegalArgumentException("Cannot delete leave balance with pending or approved leave requests");
        }

        leaveBalanceRepository.delete(leaveBalance);
    }
}
