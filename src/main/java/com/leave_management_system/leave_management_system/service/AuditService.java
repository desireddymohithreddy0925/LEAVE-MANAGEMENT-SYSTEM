package com.leave_management_system.leave_management_system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.AuditLogResponseDTO;
import com.leave_management_system.leave_management_system.entity.AuditLog;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.AuditLogRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import java.util.regex.Pattern;

@Service
public class AuditService {

    private static final Pattern SENSITIVE_JSON_PATTERN = Pattern.compile(
            "(\"(?:password|currentPassword|newPassword|confirmPassword|confirmationPassword|token|refreshToken|rawRefreshToken|secret|credentials|authorization|salary|phone|ssn|creditCard|cardNumber|cvv|pin|apiKey|secretKey)\"\\s*:\\s*\")[^\"]*(\")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SENSITIVE_KV_PATTERN = Pattern.compile(
            "(?i)(password|token|secret|refreshToken|credentials|salary|phone|ssn|creditCard|cardNumber|cvv|pin|apiKey|secretKey)\\s*=\\s*[^,\\s}]+",
            Pattern.CASE_INSENSITIVE
    );

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final SecurityEventManagementService securityEventManagementService;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this(auditLogRepository, userRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuditService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SecurityEventManagementService securityEventManagementService) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.securityEventManagementService = securityEventManagementService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public String sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String str = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            String sanitized = SENSITIVE_JSON_PATTERN.matcher(str).replaceAll("$1[REDACTED]$2");
            sanitized = SENSITIVE_KV_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
            return sanitized.length() > 1000 ? sanitized.substring(0, 997) + "..." : sanitized;
        } catch (JsonProcessingException e) {
            return "Error serializing value";
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String action, String entityType, Long entityId, Object oldValue, Object newValue) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setTimestamp(LocalDateTime.now());
        log.setOldValue(sanitizeValue(oldValue));
        log.setNewValue(sanitizeValue(newValue));

        String email = extractCurrentUsername();
        if (email != null) {
            userRepository.findByEmail(email).ifPresent(log::setUser);
        }

        log.setIpAddress(extractClientIp(null));
        auditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSecurityEvent(String action, String ipAddress, String email) {
        logSecurityEvent(action, ipAddress, email, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSecurityEvent(String action, String ipAddress, String email, String details) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType("SECURITY_EVENT");
        log.setTimestamp(LocalDateTime.now());
        log.setIpAddress(extractClientIp(ipAddress));

        if (email == null) {
            email = extractCurrentUsername();
        }

        if (email != null) {
            userRepository.findByEmail(email).ifPresent(log::setUser);
        }

        if (details != null) {
            log.setNewValue(sanitizeValue(details));
        } else if (email != null) {
            log.setNewValue(sanitizeValue("email: " + email));
        }

        auditLogRepository.save(log);

        if (securityEventManagementService != null) {
            securityEventManagementService.recordSecurityEvent(action, ipAddress, email, null, details, null);
        }
    }

    public Page<AuditLogResponseDTO> searchAuditLogs(
            Long userId,
            String action,
            String entityType,
            Long entityId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (action != null && !action.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (entityType != null && !entityType.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
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

        return auditLogRepository.findAll(spec, pageable).map(AuditLogResponseDTO::fromEntity);
    }

    public Page<AuditLogResponseDTO> searchAuditLogs(
            Long userId,
            String action,
            String entityType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        return searchAuditLogs(userId, action, entityType, null, startDate, endDate, pageable);
    }

    public AuditLogResponseDTO getAuditLogById(Long id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log not found with id: " + id));
        return AuditLogResponseDTO.fromEntity(auditLog);
    }

    public Page<AuditLogResponseDTO> getAuditLogsByEntity(String entityType, Long entityId, Pageable pageable) {
        return searchAuditLogs(null, null, entityType, entityId, null, null, pageable);
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

    private String extractClientIp(String fallbackIp) {
        if (fallbackIp != null && !fallbackIp.isEmpty() && !"unknown".equalsIgnoreCase(fallbackIp)) {
            return fallbackIp;
        }
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
        return fallbackIp;
    }
}
