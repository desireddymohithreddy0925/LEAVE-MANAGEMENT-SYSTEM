package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.AuthorizationAuditResponseDTO;
import com.leave_management_system.leave_management_system.dto.AuthorizationSummaryDTO;
import com.leave_management_system.leave_management_system.entity.AuthorizationAuditLog;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.AuthorizationAuditLogRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AuthorizationAuditService {

    private final AuthorizationAuditLogRepository authorizationAuditLogRepository;
    private final UserRepository userRepository;

    public AuthorizationAuditService(AuthorizationAuditLogRepository authorizationAuditLogRepository,
                                     UserRepository userRepository) {
        this.authorizationAuditLogRepository = authorizationAuditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuthorizationAuditLog recordDecision(String email, Long userId, String action, String resource,
                                                String permissionRequired, String decision, String reason, String ipAddress) {
        try {
            AuthorizationAuditLog auditLog = new AuthorizationAuditLog();

            if (email == null || email.trim().isEmpty()) {
                email = extractCurrentUsername();
            }
            if (email == null || email.trim().isEmpty()) {
                email = "anonymousUser";
            }
            if (email != null && email.length() > 255) {
                email = email.substring(0, 255);
            }
            auditLog.setEmail(email);

            User user = null;
            if (userId != null) {
                user = userRepository.findById(userId).orElse(null);
            }
            if (user == null && !"anonymousUser".equals(email)) {
                user = userRepository.findByEmail(email).orElse(null);
            }
            auditLog.setUser(user);

            if (action != null && action.length() > 100) {
                action = action.substring(0, 97) + "...";
            }
            auditLog.setAction(action != null ? action : "AUTHORIZATION_CHECK");

            if (resource != null && resource.length() > 255) {
                resource = resource.substring(0, 252) + "...";
            }
            auditLog.setResource(resource != null ? resource : "UNKNOWN_RESOURCE");

            if (permissionRequired != null && permissionRequired.length() > 150) {
                permissionRequired = permissionRequired.substring(0, 147) + "...";
            }
            auditLog.setPermissionRequired(permissionRequired);

            if (decision != null && decision.length() > 20) {
                decision = decision.substring(0, 20);
            }
            auditLog.setDecision(decision != null ? decision : AuthorizationAuditLog.DECISION_DENIED);

            if (reason != null && reason.length() > 500) {
                reason = reason.substring(0, 497) + "...";
            }
            auditLog.setReason(reason);

            if (ipAddress == null || ipAddress.trim().isEmpty()) {
                ipAddress = extractClientIp();
            }
            if (ipAddress != null && ipAddress.length() > 50) {
                ipAddress = ipAddress.substring(0, 50);
            }
            auditLog.setIpAddress(ipAddress);
            auditLog.setTimestamp(LocalDateTime.now());

            return authorizationAuditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Never let audit logging failure fail main business transaction
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuthorizationAuditLog recordGranted(String email, Long userId, String action, String resource,
                                               String permissionRequired, String reason) {
        return recordDecision(email, userId, action, resource, permissionRequired,
                AuthorizationAuditLog.DECISION_GRANTED, reason, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuthorizationAuditLog recordDenied(String email, Long userId, String action, String resource,
                                              String permissionRequired, String reason) {
        return recordDecision(email, userId, action, resource, permissionRequired,
                AuthorizationAuditLog.DECISION_DENIED, reason, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuthorizationAuditLog recordFromContext(String action, String resource, String permissionRequired,
                                                   String decision, String reason) {
        String email = extractCurrentUsername();
        String ip = extractClientIp();
        return recordDecision(email, null, action, resource, permissionRequired, decision, reason, ip);
    }

    public Page<AuthorizationAuditResponseDTO> searchAuthorizationAudits(
            Long userId,
            String email,
            String action,
            String resource,
            String decision,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        Specification<AuthorizationAuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (email != null && !email.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + email.trim().toLowerCase() + "%"));
            }
            if (action != null && !action.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (resource != null && !resource.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("resource")), "%" + resource.trim().toLowerCase() + "%"));
            }
            if (decision != null && !decision.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("decision")), decision.trim().toUpperCase()));
            }
            if (startDate != null && endDate != null) {
                predicates.add(cb.between(root.get("timestamp"), startDate, endDate));
            } else if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), startDate));
            } else if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return authorizationAuditLogRepository.findAll(spec, pageable)
                .map(AuthorizationAuditResponseDTO::fromEntity);
    }

    public AuthorizationSummaryDTO getSummaryMetrics() {
        long totalDecisions = authorizationAuditLogRepository.count();
        long grantedCount = authorizationAuditLogRepository.countByDecision(AuthorizationAuditLog.DECISION_GRANTED);
        long deniedCount = authorizationAuditLogRepository.countByDecision(AuthorizationAuditLog.DECISION_DENIED);

        double denialRate = 0.0;
        if (totalDecisions > 0) {
            denialRate = Math.round(((double) deniedCount / totalDecisions) * 10000.0) / 100.0;
        }

        Page<AuthorizationAuditLog> latest = authorizationAuditLogRepository.findAll(
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
        LocalDateTime lastDecisionAt = latest.hasContent() ? latest.getContent().get(0).getTimestamp() : null;

        return new AuthorizationSummaryDTO(totalDecisions, grantedCount, deniedCount, denialRate, lastDecisionAt);
    }

    public Page<AuthorizationAuditResponseDTO> getAuditsForCurrentUser(UserDetails userDetails, Pageable pageable) {
        String email = userDetails != null ? userDetails.getUsername() : extractCurrentUsername();
        if (email == null) {
            return Page.empty(pageable);
        }
        return authorizationAuditLogRepository.findByEmailOrderByTimestampDesc(email, pageable)
                .map(AuthorizationAuditResponseDTO::fromEntity);
    }

    public Page<AuthorizationAuditResponseDTO> getAuditsForUser(Long userId, Pageable pageable) {
        return authorizationAuditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable)
                .map(AuthorizationAuditResponseDTO::fromEntity);
    }

    public AuthorizationAuditResponseDTO getAuditById(Long id) {
        AuthorizationAuditLog log = authorizationAuditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Authorization audit log not found with id: " + id));
        return AuthorizationAuditResponseDTO.fromEntity(log);
    }

    private String extractCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication.getPrincipal() instanceof String && "anonymousUser".equals(authentication.getPrincipal()))) {
            String name = authentication.getName();
            if (name != null && !name.trim().isEmpty() && !"anonymousUser".equalsIgnoreCase(name)) {
                return name;
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            } else if (principal instanceof String) {
                return (String) principal;
            }
        }
        return null;
    }

    private String extractClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ipAddress = request.getHeader("X-Forwarded-For");
                if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                    ipAddress = request.getRemoteAddr();
                }
                return ipAddress;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
