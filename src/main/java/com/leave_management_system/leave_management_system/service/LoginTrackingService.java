package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LoginHistoryResponseDTO;
import com.leave_management_system.leave_management_system.dto.LoginSummaryDTO;
import com.leave_management_system.leave_management_system.entity.LoginHistory;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.LoginHistoryRepository;
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
import java.util.Optional;

@Service
public class LoginTrackingService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Value("${app.security.lockout.max-failed-attempts:5}")
    private int maxFailedAttempts = 5;

    @org.springframework.beans.factory.annotation.Value("${app.security.lockout.duration-minutes:15}")
    private int lockoutDurationMinutes = 15;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AuditService auditService;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.leave_management_system.leave_management_system.security.ClientIpResolver clientIpResolver;

    public LoginTrackingService(LoginHistoryRepository loginHistoryRepository, UserRepository userRepository) {
        this.loginHistoryRepository = loginHistoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void recordLoginSuccess(String email, String ipAddress, String userAgent) {
        String clientIp = ipAddress != null && !ipAddress.isEmpty() ? ipAddress : extractClientIp();
        String clientUa = userAgent != null && !userAgent.isEmpty() ? userAgent : extractUserAgent();

        Optional<User> userOpt = Optional.empty();
        if (email != null) {
            userOpt = userRepository.findByEmail(email);
        }

        userOpt.ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(clientIp);
            long count = user.getLoginCount() != null ? user.getLoginCount() : 0L;
            user.setLoginCount(count + 1);
            user.setFailedLoginAttempts(0);
            user.setLockExpiresAt(null);
            user.setAccountNonLocked(true);
            userRepository.save(user);
        });

        LoginHistory history = new LoginHistory();
        history.setEmail(email != null ? email : "unknown");
        userOpt.ifPresent(history::setUser);
        history.setLoginTime(LocalDateTime.now());
        history.setIpAddress(clientIp);
        history.setUserAgent(clientUa);
        history.setStatus("SUCCESS");
        history.setFailureReason(null);

        loginHistoryRepository.save(history);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(String email, String ipAddress, String userAgent, String failureReason) {
        String clientIp = ipAddress != null && !ipAddress.isEmpty() ? ipAddress : extractClientIp();
        String clientUa = userAgent != null && !userAgent.isEmpty() ? userAgent : extractUserAgent();

        LoginHistory history = new LoginHistory();
        history.setEmail(email != null ? email : "unknown");
        if (email != null) {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                history.setUser(user);
                int attempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(attempts);
                if (attempts >= maxFailedAttempts && user.isAccountNonLocked()) {
                    user.setAccountNonLocked(false);
                    user.setLockExpiresAt(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
                    if (auditService != null) {
                        auditService.logSecurityEvent(
                                com.leave_management_system.leave_management_system.security.SecurityEventType.ACCOUNT_LOCKED,
                                clientIp,
                                user.getEmail(),
                                "Account locked after " + attempts + " consecutive failed login attempts. Lockout expires in " + lockoutDurationMinutes + " minutes."
                        );
                    }
                }
                userRepository.save(user);
            }
        }
        history.setLoginTime(LocalDateTime.now());
        history.setIpAddress(clientIp);
        history.setUserAgent(clientUa);
        history.setStatus("FAILED");
        history.setFailureReason(failureReason != null ? failureReason : "Authentication failed");

        loginHistoryRepository.save(history);
    }

    public Page<LoginHistoryResponseDTO> getLoginHistoryForCurrentUser(Pageable pageable) {
        String email = extractCurrentUsername();
        if (email == null) {
            throw new ResourceNotFoundException("No authenticated user found");
        }
        return loginHistoryRepository.findByEmailIgnoreCase(email, pageable).map(LoginHistoryResponseDTO::fromEntity);
    }

    public Page<LoginHistoryResponseDTO> getLoginHistoryForUser(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return loginHistoryRepository.findByUserId(userId, pageable).map(LoginHistoryResponseDTO::fromEntity);
    }

    public Page<LoginHistoryResponseDTO> searchLoginHistory(
            Long userId,
            String email,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        Specification<LoginHistory> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (email != null && !email.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("email")), email.trim().toLowerCase()));
            }
            if (status != null && !status.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase()));
            }
            if (startDate != null && endDate != null) {
                predicates.add(cb.between(root.get("loginTime"), startDate, endDate));
            } else if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("loginTime"), startDate));
            } else if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("loginTime"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return loginHistoryRepository.findAll(spec, pageable).map(LoginHistoryResponseDTO::fromEntity);
    }

    public LoginSummaryDTO getCurrentUserSummary() {
        String email = extractCurrentUsername();
        if (email == null) {
            throw new ResourceNotFoundException("No authenticated user found");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return getSummaryForUser(user);
    }

    public LoginSummaryDTO getUserSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return getSummaryForUser(user);
    }

    private LoginSummaryDTO getSummaryForUser(User user) {
        LoginSummaryDTO summary = new LoginSummaryDTO();
        summary.setUserId(user.getId());
        summary.setEmail(user.getEmail());
        summary.setLastLoginAt(user.getLastLoginAt());
        summary.setLastLoginIp(user.getLastLoginIp());
        summary.setTotalLogins(user.getLoginCount() != null ? user.getLoginCount() : 0L);
        summary.setFailedAttempts(loginHistoryRepository.countByUserIdAndStatus(user.getId(), "FAILED"));
        return summary;
    }

    private String extractCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            if (auth.getPrincipal() instanceof UserDetails) {
                return ((UserDetails) auth.getPrincipal()).getUsername();
            } else if (auth.getPrincipal() instanceof String) {
                return (String) auth.getPrincipal();
            }
            return auth.getName();
        }
        return null;
    }

    private String extractClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                if (clientIpResolver != null) {
                    return clientIpResolver.resolveClientIp(request);
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    private String extractUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ua = request.getHeader("User-Agent");
                if (ua != null && ua.length() > 500) {
                    return ua.substring(0, 497) + "...";
                }
                return ua;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
