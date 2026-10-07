package com.leave_management_system.leave_management_system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.AuditLogResponseDTO;
import com.leave_management_system.leave_management_system.entity.AuditLog;
import com.leave_management_system.leave_management_system.repository.AuditLogRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
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
import jakarta.persistence.criteria.Predicate;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String action, String entityType, Long entityId, Object oldValue, Object newValue) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setTimestamp(LocalDateTime.now());

        try {
            if (oldValue != null) {
                String oldValStr = oldValue instanceof String ? (String) oldValue : objectMapper.writeValueAsString(oldValue);
                log.setOldValue(oldValStr.length() > 1000 ? oldValStr.substring(0, 997) + "..." : oldValStr);
            }
            if (newValue != null) {
                String newValStr = newValue instanceof String ? (String) newValue : objectMapper.writeValueAsString(newValue);
                log.setNewValue(newValStr.length() > 1000 ? newValStr.substring(0, 997) + "..." : newValStr);
            }
        } catch (JsonProcessingException e) {
            log.setOldValue("Error serializing old value");
            log.setNewValue("Error serializing new value");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication.getPrincipal() instanceof String && authentication.getPrincipal().equals("anonymousUser"))) {
            Object principal = authentication.getPrincipal();
            String email = null;
            if (principal instanceof UserDetails) {
                email = ((UserDetails) principal).getUsername();
            } else if (principal instanceof String) {
                email = (String) principal;
            }

            if (email != null) {
                userRepository.findByEmail(email).ifPresent(log::setUser);
            }
        }

        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ipAddress = request.getHeader("X-Forwarded-For");
                if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                    ipAddress = request.getRemoteAddr();
                }
                log.setIpAddress(ipAddress);
            }
        } catch (Exception e) {
            // Ignore if request context is not available (e.g. from tests or async threads)
        }

        auditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSecurityEvent(String action, String ipAddress, String email) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType("SECURITY_EVENT");
        log.setTimestamp(LocalDateTime.now());
        
        try {
            if (ipAddress == null) {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    ipAddress = request.getHeader("X-Forwarded-For");
                    if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                        ipAddress = request.getRemoteAddr();
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        log.setIpAddress(ipAddress);
        
        if (email != null) {
            userRepository.findByEmail(email).ifPresent(log::setUser);
        }

        auditLogRepository.save(log);
    }

    public Page<AuditLogResponseDTO> searchAuditLogs(Long userId, String action, String entityType, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (action != null && !action.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
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
}
