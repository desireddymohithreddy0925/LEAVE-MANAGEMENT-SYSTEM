package com.leave_management_system.leave_management_system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.SecurityEventResponseDTO;
import com.leave_management_system.leave_management_system.dto.SecurityEventSummaryDTO;
import com.leave_management_system.leave_management_system.dto.UpdateSecurityEventStatusDTO;
import com.leave_management_system.leave_management_system.entity.SecurityEvent;
import com.leave_management_system.leave_management_system.entity.SecurityEventSeverity;
import com.leave_management_system.leave_management_system.entity.SecurityEventStatus;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.SecurityEventRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.SecurityEventType;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SecurityEventManagementService {

    private static final Pattern SENSITIVE_JSON_PATTERN = Pattern.compile(
            "(\"(?:password|currentPassword|newPassword|confirmPassword|confirmationPassword|token|accessToken|access_token|refreshToken|refresh_token|rawRefreshToken|secret|clientSecret|client_secret|credentials|authorization|salary|phone|ssn|creditCard|credit_card|cardNumber|card_number|cvv|pin|apiKey|api_key|secretKey|secret_key|privateKey|private_key|authToken|auth_token|jwt|idToken|id_token)\"\\s*:\\s*)(?:\"[^\"]*\"|\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SENSITIVE_KV_PATTERN = Pattern.compile(
            "(?i)(password|currentPassword|newPassword|token|accessToken|access_token|refreshToken|refresh_token|secret|clientSecret|client_secret|credentials|authorization|salary|phone|ssn|creditCard|credit_card|cardNumber|card_number|cvv|pin|apiKey|api_key|secretKey|secret_key|privateKey|private_key|authToken|auth_token|jwt|idToken|id_token)\\s*=\\s*[^,\\s}]+",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AUTHORIZATION_HEADER_PATTERN = Pattern.compile(
            "(?i)(authorization\\s*:\\s*(?:bearer|basic)\\s+)[^\\s,\";]+",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)\\b(bearer\\s+)[A-Za-z0-9\\-_.~+/]+=*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BASIC_AUTH_PATTERN = Pattern.compile(
            "(?i)\\b(basic\\s+)[A-Za-z0-9+/]+=*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PEM_PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----",
            Pattern.CASE_INSENSITIVE
    );

    private final SecurityEventRepository securityEventRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public SecurityEventManagementService(
            SecurityEventRepository securityEventRepository,
            UserRepository userRepository) {
        this.securityEventRepository = securityEventRepository;
        this.userRepository = userRepository;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SecurityEvent recordSecurityEvent(
            String eventType,
            String ipAddress,
            String email,
            String resource,
            String details,
            String userAgent) {

        String cleanIp = extractClientIp(ipAddress);
        String cleanUserAgent = userAgent != null ? userAgent : extractUserAgent();
        String cleanEmail = email != null ? email : extractCurrentUsername();

        SecurityEvent event = new SecurityEvent();
        event.setEventType(eventType);
        event.setIpAddress(cleanIp);
        event.setUserAgent(truncate(cleanUserAgent, 500));
        event.setEmail(cleanEmail);
        event.setResource(truncate(resource, 255));
        event.setDetails(sanitizeDetails(details));
        event.setCreatedAt(LocalDateTime.now());

        if (cleanEmail != null) {
            userRepository.findByEmail(cleanEmail).ifPresent(event::setUser);
        }

        // 1. Evaluate baseline severity & default threat metrics
        applyBaselineThreatMetrics(event, eventType);

        // 2. Persist initial security event
        SecurityEvent saved = securityEventRepository.save(event);

        // 3. Automated Threat Detection evaluation
        evaluateThreatRules(saved, cleanIp, cleanEmail, cleanUserAgent, resource);

        return saved;
    }

    private void applyBaselineThreatMetrics(SecurityEvent event, String eventType) {
        if (eventType == null) {
            event.setSeverity(SecurityEventSeverity.LOW);
            event.setThreatScore(0);
            event.setStatus(SecurityEventStatus.OPEN);
            return;
        }

        switch (eventType) {
            case SecurityEventType.SUCCESSFUL_LOGIN:
            case SecurityEventType.USER_LOGOUT:
            case SecurityEventType.TOKEN_REFRESHED:
                event.setSeverity(SecurityEventSeverity.LOW);
                event.setThreatScore(0);
                event.setStatus(SecurityEventStatus.RESOLVED);
                event.setResolutionNotes("System auto-resolved routine authenticated activity");
                break;
            case SecurityEventType.PASSWORD_CHANGED:
                event.setSeverity(SecurityEventSeverity.LOW);
                event.setThreatScore(10);
                event.setStatus(SecurityEventStatus.RESOLVED);
                event.setResolutionNotes("User initiated credential update");
                break;
            case SecurityEventType.FAILED_LOGIN:
                event.setSeverity(SecurityEventSeverity.MEDIUM);
                event.setThreatScore(40);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("Authentication failure: invalid credentials");
                break;
            case SecurityEventType.EXPIRED_TOKEN:
                event.setSeverity(SecurityEventSeverity.MEDIUM);
                event.setThreatScore(25);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("Access token expired");
                break;
            case SecurityEventType.INVALID_TOKEN:
                event.setSeverity(SecurityEventSeverity.HIGH);
                event.setThreatScore(60);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("Cryptographic token signature validation failure or corrupted JWT");
                break;
            case SecurityEventType.INACTIVE_USER_TOKEN:
                event.setSeverity(SecurityEventSeverity.HIGH);
                event.setThreatScore(65);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("Token presented for deactivated or disabled account");
                break;
            case SecurityEventType.UNKNOWN_USER_TOKEN:
                event.setSeverity(SecurityEventSeverity.MEDIUM);
                event.setThreatScore(35);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("Token presented for non-existent user account");
                break;
            case SecurityEventType.UNAUTHORIZED_ACCESS:
                event.setSeverity(SecurityEventSeverity.HIGH);
                event.setThreatScore(70);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("Insufficient privilege or unauthenticated access attempt on protected endpoint");
                break;
            case SecurityEventType.BRUTE_FORCE_ATTEMPT:
                event.setSeverity(SecurityEventSeverity.CRITICAL);
                event.setThreatScore(95);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("Critical threat: Brute force login threshold breached");
                break;
            case SecurityEventType.REPEATED_UNAUTHORIZED_ACCESS:
                event.setSeverity(SecurityEventSeverity.HIGH);
                event.setThreatScore(85);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("Repeated unauthorized resource access violations");
                break;
            case SecurityEventType.SUSPICIOUS_TOKEN_ACTIVITY:
                event.setSeverity(SecurityEventSeverity.HIGH);
                event.setThreatScore(80);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("Repeated invalid token activity");
                break;
            case SecurityEventType.PRIVILEGE_ESCALATION:
                event.setSeverity(SecurityEventSeverity.CRITICAL);
                event.setThreatScore(90);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("Attempted unauthorized privilege escalation");
                break;
            default:
                event.setSeverity(SecurityEventSeverity.MEDIUM);
                event.setThreatScore(30);
                event.setStatus(SecurityEventStatus.OPEN);
                event.setThreatReason("General security event registered");
                break;
        }
    }

    private void evaluateThreatRules(
            SecurityEvent sourceEvent,
            String ipAddress,
            String email,
            String userAgent,
            String resource) {

        String eventType = sourceEvent.getEventType();

        // Rule 1: Brute Force Login Detection (>= 5 failed attempts in 15 minutes)
        if (SecurityEventType.FAILED_LOGIN.equals(eventType)) {
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(15);
            LocalDateTime cooldown = LocalDateTime.now().minusMinutes(5);

            // 1a. Target Account Brute-Force Check (repeated attempts against single account)
            if (email != null && !email.isBlank()) {
                long accountFailedCount = securityEventRepository.countByEventTypeAndEmailAndCreatedAtAfter(
                        SecurityEventType.FAILED_LOGIN, email, windowStart);

                if (accountFailedCount >= 5) {
                    long recentAlerts = securityEventRepository.countByEventTypeAndEmailAndCreatedAtAfter(
                            SecurityEventType.BRUTE_FORCE_ATTEMPT, email, cooldown);

                    if (recentAlerts == 0) {
                        SecurityEvent alert = new SecurityEvent();
                        alert.setEventType(SecurityEventType.BRUTE_FORCE_ATTEMPT);
                        alert.setSeverity(SecurityEventSeverity.CRITICAL);
                        alert.setStatus(SecurityEventStatus.OPEN);
                        alert.setUser(sourceEvent.getUser());
                        alert.setEmail(email);
                        alert.setIpAddress(ipAddress);
                        alert.setUserAgent(truncate(userAgent, 500));
                        alert.setResource(resource != null ? resource : "/api/auth/login");
                        alert.setThreatScore(95);
                        alert.setThreatReason("Automated threat detection: " + accountFailedCount + " consecutive failed login attempts detected against account '" + email + "' within 15 minutes.");
                        alert.setDetails("Target email: " + email + ", Source IP: " + (ipAddress != null ? ipAddress : "unknown"));
                        alert.setCreatedAt(LocalDateTime.now());
                        securityEventRepository.save(alert);
                    }
                }
            }

            // 1b. Source IP Brute-Force / Credential Stuffing Check (repeated attempts from single IP across any accounts)
            if (ipAddress != null && !ipAddress.isBlank()) {
                long ipFailedCount = securityEventRepository.countByEventTypeAndIpAddressAndCreatedAtAfter(
                        SecurityEventType.FAILED_LOGIN, ipAddress, windowStart);

                if (ipFailedCount >= 5) {
                    long recentIpAlerts = securityEventRepository.countByEventTypeAndIpAddressAndCreatedAtAfter(
                            SecurityEventType.BRUTE_FORCE_ATTEMPT, ipAddress, cooldown);

                    if (recentIpAlerts == 0) {
                        SecurityEvent alert = new SecurityEvent();
                        alert.setEventType(SecurityEventType.BRUTE_FORCE_ATTEMPT);
                        alert.setSeverity(SecurityEventSeverity.CRITICAL);
                        alert.setStatus(SecurityEventStatus.OPEN);
                        alert.setUser(sourceEvent.getUser());
                        alert.setEmail(email);
                        alert.setIpAddress(ipAddress);
                        alert.setUserAgent(truncate(userAgent, 500));
                        alert.setResource(resource != null ? resource : "/api/auth/login");
                        alert.setThreatScore(95);
                        alert.setThreatReason("Automated threat detection: " + ipFailedCount + " failed login attempts detected from IP " + ipAddress + " across accounts within 15 minutes.");
                        alert.setDetails("Source IP: " + ipAddress + ", Target account: " + (email != null ? email : "multiple"));
                        alert.setCreatedAt(LocalDateTime.now());
                        securityEventRepository.save(alert);
                    }
                }
            }
        }

        // Rule 2: Repeated Unauthorized Access Violations (>= 3 attempts in 10 minutes)
        if (SecurityEventType.UNAUTHORIZED_ACCESS.equals(eventType) && ipAddress != null && !ipAddress.isBlank()) {
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(10);
            long unauthCount = securityEventRepository.countByEventTypeAndIpAddressAndCreatedAtAfter(
                    SecurityEventType.UNAUTHORIZED_ACCESS, ipAddress, windowStart);

            if (unauthCount >= 3) {
                LocalDateTime cooldown = LocalDateTime.now().minusMinutes(5);
                long recentAlerts = securityEventRepository.countByEventTypeAndIpAddressAndCreatedAtAfter(
                        SecurityEventType.REPEATED_UNAUTHORIZED_ACCESS, ipAddress, cooldown);

                if (recentAlerts == 0) {
                    SecurityEvent alert = new SecurityEvent();
                    alert.setEventType(SecurityEventType.REPEATED_UNAUTHORIZED_ACCESS);
                    alert.setSeverity(SecurityEventSeverity.HIGH);
                    alert.setStatus(SecurityEventStatus.OPEN);
                    alert.setUser(sourceEvent.getUser());
                    alert.setEmail(email);
                    alert.setIpAddress(ipAddress);
                    alert.setUserAgent(truncate(userAgent, 500));
                    alert.setResource(resource);
                    alert.setThreatScore(85);
                    alert.setThreatReason("Automated threat detection: " + unauthCount + " unauthorized access attempts within 10 minutes.");
                    alert.setDetails("Source IP: " + ipAddress + ", Target Resource: " + resource);
                    alert.setCreatedAt(LocalDateTime.now());
                    securityEventRepository.save(alert);
                }
            }
        }

        // Rule 3: Suspicious Token Authentication Failures (>= 4 attempts in 10 minutes)
        if ((SecurityEventType.INVALID_TOKEN.equals(eventType) || SecurityEventType.EXPIRED_TOKEN.equals(eventType))
                && ipAddress != null && !ipAddress.isBlank()) {
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(10);
            long tokenErrors = securityEventRepository.countByEventTypeInAndIpAddressAndCreatedAtAfter(
                    List.of(SecurityEventType.INVALID_TOKEN, SecurityEventType.EXPIRED_TOKEN), ipAddress, windowStart);

            if (tokenErrors >= 4) {
                LocalDateTime cooldown = LocalDateTime.now().minusMinutes(5);
                long recentAlerts = securityEventRepository.countByEventTypeAndIpAddressAndCreatedAtAfter(
                        SecurityEventType.SUSPICIOUS_TOKEN_ACTIVITY, ipAddress, cooldown);

                if (recentAlerts == 0) {
                    SecurityEvent alert = new SecurityEvent();
                    alert.setEventType(SecurityEventType.SUSPICIOUS_TOKEN_ACTIVITY);
                    alert.setSeverity(SecurityEventSeverity.HIGH);
                    alert.setStatus(SecurityEventStatus.OPEN);
                    alert.setUser(sourceEvent.getUser());
                    alert.setEmail(email);
                    alert.setIpAddress(ipAddress);
                    alert.setUserAgent(truncate(userAgent, 500));
                    alert.setResource(resource);
                    alert.setThreatScore(80);
                    alert.setThreatReason("Automated threat detection: " + tokenErrors + " token authentication failures within 10 minutes.");
                    alert.setDetails("Source IP: " + ipAddress);
                    alert.setCreatedAt(LocalDateTime.now());
                    securityEventRepository.save(alert);
                }
            }
        }
    }

    public Page<SecurityEventResponseDTO> searchSecurityEvents(
            String eventType,
            SecurityEventSeverity severity,
            SecurityEventStatus status,
            Long userId,
            String email,
            String ipAddress,
            Integer minThreatScore,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        Specification<SecurityEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (eventType != null && !eventType.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("eventType"), eventType.trim()));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (email != null && !email.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + email.trim().toLowerCase() + "%"));
            }
            if (ipAddress != null && !ipAddress.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("ipAddress"), ipAddress.trim()));
            }
            if (minThreatScore != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("threatScore"), minThreatScore));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return securityEventRepository.findAll(spec, pageable).map(this::mapToDTO);
    }

    public SecurityEventResponseDTO getSecurityEventById(Long id) {
        SecurityEvent event = securityEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Security event not found with ID: " + id));
        return mapToDTO(event);
    }

    private static final Map<SecurityEventStatus, Set<SecurityEventStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            SecurityEventStatus.OPEN, Set.of(SecurityEventStatus.INVESTIGATING, SecurityEventStatus.RESOLVED, SecurityEventStatus.FALSE_POSITIVE),
            SecurityEventStatus.INVESTIGATING, Set.of(SecurityEventStatus.OPEN, SecurityEventStatus.RESOLVED, SecurityEventStatus.FALSE_POSITIVE),
            SecurityEventStatus.RESOLVED, Set.of(SecurityEventStatus.OPEN, SecurityEventStatus.INVESTIGATING, SecurityEventStatus.FALSE_POSITIVE),
            SecurityEventStatus.FALSE_POSITIVE, Set.of(SecurityEventStatus.OPEN, SecurityEventStatus.INVESTIGATING, SecurityEventStatus.RESOLVED)
    );

    @Transactional
    public SecurityEventResponseDTO updateStatus(
            Long id,
            UpdateSecurityEventStatusDTO dto,
            Authentication authentication) {

        if (dto == null || dto.getStatus() == null) {
            throw new IllegalArgumentException("Target status must not be null");
        }

        SecurityEvent event = securityEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Security event not found with ID: " + id));

        SecurityEventStatus currentStatus = event.getStatus();
        SecurityEventStatus targetStatus = dto.getStatus();

        validateStatusTransition(currentStatus, targetStatus);

        event.setStatus(targetStatus);
        if (dto.getResolutionNotes() != null && !dto.getResolutionNotes().isBlank()) {
            event.setResolutionNotes(dto.getResolutionNotes());
        }

        // Keep resolution timestamps and reviewer information consistent
        if (targetStatus == SecurityEventStatus.RESOLVED || targetStatus == SecurityEventStatus.FALSE_POSITIVE) {
            event.setResolvedAt(LocalDateTime.now());
            if (authentication != null && authentication.getName() != null) {
                userRepository.findByEmail(authentication.getName()).ifPresent(event::setResolvedBy);
            }
        } else {
            // Reopened (OPEN) or marked INVESTIGATING: clear resolution metadata
            event.setResolvedAt(null);
            event.setResolvedBy(null);
        }

        SecurityEvent saved = securityEventRepository.save(event);
        return mapToDTO(saved);
    }

    private void validateStatusTransition(SecurityEventStatus currentStatus, SecurityEventStatus targetStatus) {
        if (currentStatus == targetStatus) {
            throw new IllegalArgumentException("Security event is already in status: " + currentStatus);
        }
        Set<SecurityEventStatus> allowed = ALLOWED_STATUS_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new IllegalArgumentException("Invalid status transition from " + currentStatus + " to " + targetStatus);
        }
    }

    public SecurityEventSummaryDTO getSummary() {
        long totalEvents = securityEventRepository.count();
        long openCount = securityEventRepository.countByStatus(SecurityEventStatus.OPEN);
        long investigatingCount = securityEventRepository.countByStatus(SecurityEventStatus.INVESTIGATING);
        long resolvedCount = securityEventRepository.countByStatus(SecurityEventStatus.RESOLVED);
        long falsePositiveCount = securityEventRepository.countByStatus(SecurityEventStatus.FALSE_POSITIVE);

        long lowSeverity = securityEventRepository.countBySeverity(SecurityEventSeverity.LOW);
        long mediumSeverity = securityEventRepository.countBySeverity(SecurityEventSeverity.MEDIUM);
        long highSeverity = securityEventRepository.countBySeverity(SecurityEventSeverity.HIGH);
        long criticalSeverity = securityEventRepository.countBySeverity(SecurityEventSeverity.CRITICAL);

        long unresolvedHighRisk = securityEventRepository.countByStatusAndSeverityIn(
                SecurityEventStatus.OPEN, List.of(SecurityEventSeverity.HIGH, SecurityEventSeverity.CRITICAL))
                + securityEventRepository.countByStatusAndSeverityIn(
                SecurityEventStatus.INVESTIGATING, List.of(SecurityEventSeverity.HIGH, SecurityEventSeverity.CRITICAL));

        double resolutionRate = totalEvents > 0
                ? Math.round(((double) (resolvedCount + falsePositiveCount) / totalEvents) * 1000.0) / 10.0
                : 100.0;

        SecurityEventSummaryDTO summary = new SecurityEventSummaryDTO();
        summary.setTotalEvents(totalEvents);
        summary.setOpenCount(openCount);
        summary.setInvestigatingCount(investigatingCount);
        summary.setResolvedCount(resolvedCount);
        summary.setFalsePositiveCount(falsePositiveCount);
        summary.setLowSeverityCount(lowSeverity);
        summary.setMediumSeverityCount(mediumSeverity);
        summary.setHighSeverityCount(highSeverity);
        summary.setCriticalSeverityCount(criticalSeverity);
        summary.setUnresolvedHighRiskCount(unresolvedHighRisk);
        summary.setResolutionRatePercentage(resolutionRate);

        return summary;
    }

    public SecurityEventResponseDTO mapToDTO(SecurityEvent event) {
        SecurityEventResponseDTO dto = new SecurityEventResponseDTO();
        dto.setId(event.getId());
        dto.setEventType(event.getEventType());
        dto.setSeverity(event.getSeverity());
        dto.setStatus(event.getStatus());
        dto.setEmail(event.getEmail());
        dto.setIpAddress(event.getIpAddress());
        dto.setUserAgent(event.getUserAgent());
        dto.setResource(event.getResource());
        dto.setThreatScore(event.getThreatScore());
        dto.setThreatReason(event.getThreatReason());
        dto.setResolutionNotes(event.getResolutionNotes());
        dto.setResolvedAt(event.getResolvedAt());
        dto.setDetails(event.getDetails());
        dto.setCreatedAt(event.getCreatedAt());

        if (event.getUser() != null) {
            dto.setUserId(event.getUser().getId());
            dto.setUserEmail(event.getUser().getEmail());
        }
        if (event.getResolvedBy() != null) {
            dto.setResolvedById(event.getResolvedBy().getId());
            dto.setResolvedByEmail(event.getResolvedBy().getEmail());
        }

        return dto;
    }

    public String sanitizeDetails(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String str = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            String sanitized = PEM_PRIVATE_KEY_PATTERN.matcher(str).replaceAll("[REDACTED_PRIVATE_KEY]");
            sanitized = AUTHORIZATION_HEADER_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
            sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
            sanitized = BASIC_AUTH_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]");
            sanitized = SENSITIVE_JSON_PATTERN.matcher(sanitized).replaceAll("$1\"[REDACTED]\"");
            sanitized = SENSITIVE_KV_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
            return truncate(sanitized, 4000);
        } catch (JsonProcessingException e) {
            return "Sanitization error";
        }
    }

    private String extractClientIp(String ipAddress) {
        if (ipAddress != null && !ipAddress.isBlank()) {
            return ipAddress;
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return "127.0.0.1";
    }

    private String extractUserAgent() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            return request.getHeader("User-Agent");
        }
        return null;
    }

    private String extractCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            if (auth.getPrincipal() instanceof UserDetails) {
                return ((UserDetails) auth.getPrincipal()).getUsername();
            }
            return auth.getName();
        }
        return null;
    }

    private String truncate(String val, int maxLen) {
        if (val == null) {
            return null;
        }
        return val.length() > maxLen ? val.substring(0, maxLen - 3) + "..." : val;
    }
}
