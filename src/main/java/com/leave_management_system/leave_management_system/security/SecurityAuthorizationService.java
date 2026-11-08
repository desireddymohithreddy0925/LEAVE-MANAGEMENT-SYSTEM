package com.leave_management_system.leave_management_system.security;

import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
import com.leave_management_system.leave_management_system.entity.TransactionReview;
import com.leave_management_system.leave_management_system.repository.TransactionReviewRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("securityService")
public class SecurityAuthorizationService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AuthorizationAuditService authorizationAuditService;
    private final TransactionReviewRepository transactionReviewRepository;
    private final UserRepository userRepository;

    public SecurityAuthorizationService(EmployeeRepository employeeRepository,
                                        LeaveRequestRepository leaveRequestRepository,
                                        @Lazy AuthorizationAuditService authorizationAuditService,
                                        @Lazy TransactionReviewRepository transactionReviewRepository,
                                        @Lazy UserRepository userRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.authorizationAuditService = authorizationAuditService;
        this.transactionReviewRepository = transactionReviewRepository;
        this.userRepository = userRepository;
    }

    public Optional<Employee> getAuthenticatedEmployee(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String email = authentication.getName();
        return employeeRepository.findByUserEmail(email);
    }

    public boolean isSelf(Authentication authentication, Long targetId) {
        if (authentication == null || !authentication.isAuthenticated() || targetId == null) {
            return false;
        }
        String email = authentication.getName();
        Optional<Employee> emp = employeeRepository.findByUserEmail(email);
        if (emp.isPresent()) {
            Employee e = emp.get();
            if (targetId.equals(e.getId()) || (e.getUser() != null && targetId.equals(e.getUser().getId()))) {
                return true;
            }
        }
        if (userRepository != null) {
            return userRepository.findByEmail(email)
                    .map(u -> targetId.equals(u.getId()))
                    .orElse(false);
        }
        return false;
    }

    public boolean isManagerOfEmployee(Authentication authentication, Long targetEmployeeId) {
        return getAuthenticatedEmployee(authentication)
                .map(manager -> isManagerOfEmployee(manager.getId(), targetEmployeeId))
                .orElse(false);
    }

    public boolean isManagerOfEmployee(Long managerId, Long targetEmployeeId) {
        if (managerId == null || targetEmployeeId == null || managerId.equals(targetEmployeeId)) {
            return false;
        }

        return employeeRepository.findById(targetEmployeeId)
                .map(targetEmp -> {
                    // 1. Direct manager check
                    if (targetEmp.getManager() != null && targetEmp.getManager().getId().equals(managerId)) {
                        return true;
                    }

                    // 2. Department manager check
                    Department dept = targetEmp.getDepartment();
                    if (dept != null && dept.getManager() != null && dept.getManager().getId().equals(managerId)) {
                        return true;
                    }

                    // 3. Multi-level reporting hierarchy traversal
                    Employee current = targetEmp.getManager();
                    java.util.Set<Long> visited = new java.util.HashSet<>();
                    while (current != null && visited.add(current.getId())) {
                        if (current.getId().equals(managerId)) {
                            return true;
                        }
                        current = current.getManager();
                    }

                    return false;
                })
                .orElse(false);
    }

    private void audit(Authentication auth, String action, String resource, String permission, boolean granted, String reason) {
        if (authorizationAuditService != null) {
            String email = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                    ? auth.getName() : "anonymousUser";
            if (granted) {
                authorizationAuditService.recordGranted(email, null, action, resource, permission, reason);
            } else {
                authorizationAuditService.recordDenied(email, null, action, resource, permission, reason);
            }
        }
    }

    public boolean canViewManagerTeam(Authentication authentication, Long targetManagerId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            audit(authentication, "TEAM_VIEW_CHECK", "Manager#" + targetManagerId, "LEAVE_VIEW_TEAM", false, "Unauthenticated caller");
            return false;
        }

        boolean isGlobalAdminOrHr = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        boolean hasEmployeeView = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("EMPLOYEE_VIEW"));
        if (isGlobalAdminOrHr && hasEmployeeView) {
            audit(authentication, "TEAM_VIEW_CHECK", "Manager#" + targetManagerId, "EMPLOYEE_VIEW", true, "Authorized via administrative role");
            return true;
        }

        boolean hasTeamView = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_VIEW_TEAM"));
        if (hasTeamView) {
            boolean matches = getAuthenticatedEmployee(authentication)
                    .map(emp -> emp.getId().equals(targetManagerId))
                    .orElse(false);
            if (matches) {
                audit(authentication, "TEAM_VIEW_CHECK", "Manager#" + targetManagerId, "LEAVE_VIEW_TEAM", true, "Authorized as self manager");
                return true;
            }
        }

        audit(authentication, "TEAM_VIEW_CHECK", "Manager#" + targetManagerId, "LEAVE_VIEW_TEAM", false, "User cannot view manager team");
        return false;
    }

    public boolean canViewEmployee(Authentication authentication, Long targetEmployeeId) {
        boolean isGlobalAdminOrHr = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isGlobalAdminOrHr) {
            audit(authentication, "EMPLOYEE_VIEW_CHECK", "Employee#" + targetEmployeeId, "ROLE_ADMIN/ROLE_HR", true, "Authorized via administrative role");
            return true;
        }

        if (isSelf(authentication, targetEmployeeId)) {
            audit(authentication, "EMPLOYEE_VIEW_CHECK", "Employee#" + targetEmployeeId, "SELF", true, "Authorized as self");
            return true;
        }

        if (isManagerOfEmployee(authentication, targetEmployeeId)) {
            audit(authentication, "EMPLOYEE_VIEW_CHECK", "Employee#" + targetEmployeeId, "MANAGER", true, "Authorized as manager of employee");
            return true;
        }

        audit(authentication, "EMPLOYEE_VIEW_CHECK", "Employee#" + targetEmployeeId, "EMPLOYEE_VIEW", false, "Caller is neither self nor manager");
        return false;
    }

    public boolean canManageLeaveBalance(Authentication authentication, Long targetEmployeeId) {
        boolean hasGlobalManage = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_BALANCE_MANAGE"));
        if (hasGlobalManage) {
            audit(authentication, "BALANCE_MANAGE_CHECK", "Employee#" + targetEmployeeId, "LEAVE_BALANCE_MANAGE", true, "Authorized via LEAVE_BALANCE_MANAGE permission");
            return true;
        }

        if (isManagerOfEmployee(authentication, targetEmployeeId)) {
            audit(authentication, "BALANCE_MANAGE_CHECK", "Employee#" + targetEmployeeId, "MANAGER", true, "Authorized as manager of employee");
            return true;
        }

        audit(authentication, "BALANCE_MANAGE_CHECK", "Employee#" + targetEmployeeId, "LEAVE_BALANCE_MANAGE", false, "User lacks permission to manage balance for employee " + targetEmployeeId);
        return false;
    }

    public boolean canApproveLeaveRequest(Authentication authentication, Long leaveRequestId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            audit(authentication, "LEAVE_APPROVAL_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_APPROVE", false, "Unauthenticated caller");
            return false;
        }

        boolean hasPermission = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_APPROVE"));
        if (!hasPermission) {
            audit(authentication, "LEAVE_APPROVAL_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_APPROVE", false, "User lacks LEAVE_APPROVE permission");
            return false;
        }

        boolean isGlobalScope = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isGlobalScope) {
            audit(authentication, "LEAVE_APPROVAL_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_APPROVE", true, "Authorized via global administrative role");
            return true;
        }

        Optional<LeaveRequest> leaveRequest = leaveRequestRepository.findById(leaveRequestId);
        if (leaveRequest.isEmpty()) {
            audit(authentication, "LEAVE_APPROVAL_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_APPROVE", false, "Leave request not found");
            return false;
        }
        
        Long targetEmployeeId = leaveRequest.get().getEmployee().getId();
        boolean isManager = isManagerOfEmployee(authentication, targetEmployeeId);
        if (isManager) {
            audit(authentication, "LEAVE_APPROVAL_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_APPROVE", true, "Authorized as manager of applicant");
            return true;
        } else {
            audit(authentication, "LEAVE_APPROVAL_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_APPROVE", false, "User is not a manager of applicant " + targetEmployeeId);
            return false;
        }
    }

    public boolean canRejectLeaveRequest(Authentication authentication, Long leaveRequestId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            audit(authentication, "LEAVE_REJECTION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_REJECT", false, "Unauthenticated caller");
            return false;
        }

        boolean hasPermission = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_REJECT"));
        if (!hasPermission) {
            audit(authentication, "LEAVE_REJECTION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_REJECT", false, "User lacks LEAVE_REJECT permission");
            return false;
        }

        boolean isGlobalScope = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isGlobalScope) {
            audit(authentication, "LEAVE_REJECTION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_REJECT", true, "Authorized via global administrative role");
            return true;
        }

        Optional<LeaveRequest> leaveRequest = leaveRequestRepository.findById(leaveRequestId);
        if (leaveRequest.isEmpty()) {
            audit(authentication, "LEAVE_REJECTION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_REJECT", false, "Leave request not found");
            return false;
        }
        
        Long targetEmployeeId = leaveRequest.get().getEmployee().getId();
        boolean isManager = isManagerOfEmployee(authentication, targetEmployeeId);
        if (isManager) {
            audit(authentication, "LEAVE_REJECTION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_REJECT", true, "Authorized as manager of applicant");
            return true;
        } else {
            audit(authentication, "LEAVE_REJECTION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_REJECT", false, "User is not a manager of applicant " + targetEmployeeId);
            return false;
        }
    }

    public boolean canViewLeaveRequest(Authentication authentication, Long leaveRequestId) {
        boolean hasGlobalView = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_VIEW_ALL"));
        if (hasGlobalView) {
            audit(authentication, "LEAVE_VIEW_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_VIEW_ALL", true, "Authorized via LEAVE_VIEW_ALL permission");
            return true;
        }

        Optional<LeaveRequest> leaveRequest = leaveRequestRepository.findById(leaveRequestId);
        if (leaveRequest.isEmpty()) {
            audit(authentication, "LEAVE_VIEW_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_VIEW", false, "Leave request not found");
            return false;
        }

        Long targetEmployeeId = leaveRequest.get().getEmployee().getId();
        if (isSelf(authentication, targetEmployeeId)) {
            audit(authentication, "LEAVE_VIEW_CHECK", "LeaveRequest#" + leaveRequestId, "SELF", true, "Authorized as leave applicant");
            return true;
        }

        if (isManagerOfEmployee(authentication, targetEmployeeId)) {
            audit(authentication, "LEAVE_VIEW_CHECK", "LeaveRequest#" + leaveRequestId, "MANAGER", true, "Authorized as manager of applicant");
            return true;
        }

        audit(authentication, "LEAVE_VIEW_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_VIEW", false, "User is not authorized to view leave request");
        return false;
    }

    public boolean canCancelLeaveRequest(Authentication authentication, Long leaveRequestId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            audit(authentication, "LEAVE_CANCELLATION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_CANCEL", false, "Unauthenticated caller");
            return false;
        }

        boolean isGlobalScope = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isGlobalScope) {
            audit(authentication, "LEAVE_CANCELLATION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_CANCEL", true, "Authorized via global administrative role");
            return true;
        }

        boolean hasCancelPermission = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_CANCEL"));
        if (!hasCancelPermission) {
            audit(authentication, "LEAVE_CANCELLATION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_CANCEL", false, "User lacks LEAVE_CANCEL permission");
            return false;
        }

        Optional<LeaveRequest> leaveRequest = leaveRequestRepository.findById(leaveRequestId);
        if (leaveRequest.isEmpty()) {
            audit(authentication, "LEAVE_CANCELLATION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_CANCEL", false, "Leave request not found");
            return false;
        }

        Long targetEmployeeId = leaveRequest.get().getEmployee().getId();
        boolean self = isSelf(authentication, targetEmployeeId);
        if (self) {
            audit(authentication, "LEAVE_CANCELLATION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_CANCEL", true, "Authorized as applicant cancelling own leave");
            return true;
        } else {
            audit(authentication, "LEAVE_CANCELLATION_CHECK", "LeaveRequest#" + leaveRequestId, "LEAVE_CANCEL", false, "User can only cancel their own leave requests");
            return false;
        }
    }

    public boolean canViewTransactionReview(Authentication authentication, Long reviewId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            audit(authentication, "TRANSACTION_REVIEW_VIEW_CHECK", "TransactionReview#" + reviewId, "REVIEW_VIEW", false, "Unauthenticated caller");
            return false;
        }
        boolean isGlobalScope = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR")
                        || a.getAuthority().equals("AUDIT_VIEW") || a.getAuthority().equals("ADMIN_AUDIT_VIEW"));
        if (isGlobalScope) {
            audit(authentication, "TRANSACTION_REVIEW_VIEW_CHECK", "TransactionReview#" + reviewId, "REVIEW_VIEW", true, "Authorized via global audit role");
            return true;
        }

        if (reviewId == null) {
            audit(authentication, "TRANSACTION_REVIEW_VIEW_CHECK", "TransactionReview#null", "REVIEW_VIEW", false, "Review ID is null");
            return false;
        }

        Optional<TransactionReview> reviewOpt = transactionReviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            audit(authentication, "TRANSACTION_REVIEW_VIEW_CHECK", "TransactionReview#" + reviewId, "REVIEW_VIEW", false, "Review not found");
            return false;
        }

        TransactionReview review = reviewOpt.get();
        String callerEmail = authentication.getName();

        if (review.getActorEmail() != null && review.getActorEmail().equalsIgnoreCase(callerEmail)) {
            audit(authentication, "TRANSACTION_REVIEW_VIEW_CHECK", "TransactionReview#" + reviewId, "SELF", true, "Authorized as actor");
            return true;
        }

        if (review.getEmployee() != null && review.getEmployee().getUser() != null && review.getEmployee().getUser().getEmail() != null) {
            if (review.getEmployee().getUser().getEmail().equalsIgnoreCase(callerEmail)) {
                audit(authentication, "TRANSACTION_REVIEW_VIEW_CHECK", "TransactionReview#" + reviewId, "SELF", true, "Authorized as subject employee");
                return true;
            }
        }

        if (review.getEmployee() != null) {
            boolean isManager = isManagerOfEmployee(authentication, review.getEmployee().getId());
            if (isManager) {
                audit(authentication, "TRANSACTION_REVIEW_VIEW_CHECK", "TransactionReview#" + reviewId, "MANAGER", true, "Authorized as manager of employee");
                return true;
            }
        }

        audit(authentication, "TRANSACTION_REVIEW_VIEW_CHECK", "TransactionReview#" + reviewId, "REVIEW_VIEW", false, "User lacks authorization to view transaction review");
        return false;
    }
}

