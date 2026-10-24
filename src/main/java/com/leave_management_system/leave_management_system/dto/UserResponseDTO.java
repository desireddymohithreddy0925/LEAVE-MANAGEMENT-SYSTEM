package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public class UserResponseDTO {

    private Long id;
    private String email;
    private boolean active;
    private Set<RoleResponseDTO> roles;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Long loginCount;
    private boolean accountNonLocked;
    private int failedLoginAttempts;
    private LocalDateTime lockExpiresAt;

    public static UserResponseDTO fromEntity(User user) {
        if (user == null) {
            return null;
        }
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setActive(user.isActive());
        dto.setAccountNonLocked(user.isAccountNonLocked());
        dto.setFailedLoginAttempts(user.getFailedLoginAttempts());
        dto.setLockExpiresAt(user.getLockExpiresAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setLastLoginIp(user.getLastLoginIp());
        dto.setLoginCount(user.getLoginCount());
        if (user.getRoles() != null) {
            dto.setRoles(user.getRoles().stream()
                    .map(RoleResponseDTO::fromEntity)
                    .collect(Collectors.toSet()));
        }
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<RoleResponseDTO> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleResponseDTO> roles) {
        this.roles = roles;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public Long getLoginCount() {
        return loginCount;
    }

    public void setLoginCount(Long loginCount) {
        this.loginCount = loginCount;
    }

    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    public void setAccountNonLocked(boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLockExpiresAt() {
        return lockExpiresAt;
    }

    public void setLockExpiresAt(LocalDateTime lockExpiresAt) {
        this.lockExpiresAt = lockExpiresAt;
    }
}
