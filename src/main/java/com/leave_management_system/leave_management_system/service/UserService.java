package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.UserResponseDTO;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.SecurityEventType;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, @Lazy AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE') or hasAuthority('USER_LOCK_MANAGE')")
    public Page<UserResponseDTO> getAllUsers(Boolean locked, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            if (locked != null) {
                return cb.equal(root.get("accountNonLocked"), !locked);
            }
            return cb.conjunction();
        };
        return userRepository.findAll(spec, pageable).map(UserResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE') or hasAuthority('USER_LOCK_MANAGE') or @securityService.isSelf(authentication, #id)")
    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return UserResponseDTO.fromEntity(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE') or hasAuthority('USER_LOCK_MANAGE')")
    public UserResponseDTO unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setAccountNonLocked(true);
        user.setFailedLoginAttempts(0);
        user.setLockExpiresAt(null);
        User saved = userRepository.save(user);

        if (auditService != null) {
            auditService.logSecurityEvent(
                    SecurityEventType.ACCOUNT_UNLOCKED,
                    null,
                    user.getEmail(),
                    "User account manually unlocked by administrator"
            );
        }

        return UserResponseDTO.fromEntity(saved);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE') or hasAuthority('USER_LOCK_MANAGE')")
    public UserResponseDTO lockUser(Long id, String reason) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setAccountNonLocked(false);
        user.setLockExpiresAt(null); // Indefinite lock until unlocked
        User saved = userRepository.save(user);

        if (auditService != null) {
            String details = (reason != null && !reason.isBlank()) ? reason.trim() : "Manually locked by administrator";
            auditService.logSecurityEvent(
                    SecurityEventType.ACCOUNT_LOCKED,
                    null,
                    user.getEmail(),
                    "User account manually locked: " + details
            );
        }

        return UserResponseDTO.fromEntity(saved);
    }
}
