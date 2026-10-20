package com.leave_management_system.leave_management_system.security.sensitive;

import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.service.AuditService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataAnonymizationService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final AuthorizationAuditLogRepository authorizationAuditLogRepository;
    private final AuditService auditService;

    public DataAnonymizationService(EmployeeRepository employeeRepository,
                                    UserRepository userRepository,
                                    LeaveRequestRepository leaveRequestRepository,
                                    LeaveBalanceRepository leaveBalanceRepository,
                                    LoginHistoryRepository loginHistoryRepository,
                                    AuthorizationAuditLogRepository authorizationAuditLogRepository,
                                    AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.authorizationAuditLogRepository = authorizationAuditLogRepository;
        this.auditService = auditService;
    }

    /**
     * Anonymizes employee PII and deactivates associated account for GDPR Right to Erasure compliance.
     */
    @Transactional
    public AnonymizeResponseDTO anonymizeEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        String oldEmail = employee.getUser() != null ? employee.getUser().getEmail() : null;

        employee.setFirstName("Anonymized");
        employee.setLastName("Employee");
        employee.setPhone(null);
        employee.setSalary(BigDecimal.ZERO);
        employee.setStatus("INACTIVE");

        if (employee.getUser() != null) {
            User user = employee.getUser();
            user.setEmail("anonymized." + employee.getId() + "@anonymized.local");
            user.setPassword("ANONYMIZED_DISABLED");
            user.setActive(false);
            user.setLastLoginIp(null);
            userRepository.save(user);
        }

        employeeRepository.save(employee);

        try {
            auditService.logAction("DATA_ANONYMIZED", "Employee", employee.getId(), oldEmail, "ANONYMIZED");
        } catch (Exception ignored) {
        }

        return new AnonymizeResponseDTO(
                employee.getId(),
                LocalDateTime.now(),
                "ANONYMIZED",
                "Employee PII has been successfully anonymized in compliance with GDPR Right to Erasure."
        );
    }

    /**
     * Exports all personal data held for an employee for GDPR Right to Data Portability compliance.
     */
    @Transactional(readOnly = true)
    public PersonalDataExportDTO exportPersonalData(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        PersonalDataExportDTO export = new PersonalDataExportDTO();
        export.setExportTimestamp(LocalDateTime.now());
        export.setEmployee(EmployeeResponseDTO.fromEntity(employee));

        if (employee.getUser() != null) {
            User user = employee.getUser();
            export.setUser(UserResponseDTO.fromEntity(user));

            try {
                List<LoginHistoryResponseDTO> loginHistory = loginHistoryRepository
                        .findTop10ByUserIdOrderByLoginTimeDesc(user.getId())
                        .stream()
                        .map(LoginHistoryResponseDTO::fromEntity)
                        .toList();
                export.setRecentLoginHistory(loginHistory);
            } catch (Exception e) {
                export.setRecentLoginHistory(new ArrayList<>());
            }

            try {
                List<AuthorizationAuditResponseDTO> authAudits = authorizationAuditLogRepository
                        .findByUserIdOrderByTimestampDesc(user.getId(), PageRequest.of(0, 10))
                        .getContent()
                        .stream()
                        .map(AuthorizationAuditResponseDTO::fromEntity)
                        .toList();
                export.setRecentAuthorizationAudits(authAudits);
            } catch (Exception e) {
                export.setRecentAuthorizationAudits(new ArrayList<>());
            }
        }

        try {
            List<LeaveResponseDTO> leaves = leaveRequestRepository.findByEmployee(employee)
                    .stream()
                    .map(LeaveResponseDTO::fromEntity)
                    .toList();
            export.setLeaveRequests(leaves);
        } catch (Exception e) {
            export.setLeaveRequests(new ArrayList<>());
        }

        try {
            List<LeaveBalanceResponseDTO> balances = leaveBalanceRepository.findByEmployee(employee)
                    .stream()
                    .map(LeaveBalanceResponseDTO::fromEntity)
                    .toList();
            export.setLeaveBalances(balances);
        } catch (Exception e) {
            export.setLeaveBalances(new ArrayList<>());
        }

        return export;
    }
}
