package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.LoginHistory;

import java.time.LocalDateTime;

public class LoginHistoryResponseDTO {

    private Long id;
    private Long userId;
    private String userEmail;
    private LocalDateTime loginTime;
    private String ipAddress;
    private String userAgent;
    private String status;
    private String failureReason;

    public static LoginHistoryResponseDTO fromEntity(LoginHistory entity) {
        if (entity == null) {
            return null;
        }
        LoginHistoryResponseDTO dto = new LoginHistoryResponseDTO();
        dto.setId(entity.getId());
        if (entity.getUser() != null) {
            dto.setUserId(entity.getUser().getId());
            dto.setUserEmail(entity.getUser().getEmail());
        } else {
            dto.setUserEmail(entity.getEmail());
        }
        dto.setLoginTime(entity.getLoginTime());
        dto.setIpAddress(entity.getIpAddress());
        dto.setUserAgent(entity.getUserAgent());
        dto.setStatus(entity.getStatus());
        dto.setFailureReason(entity.getFailureReason());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
