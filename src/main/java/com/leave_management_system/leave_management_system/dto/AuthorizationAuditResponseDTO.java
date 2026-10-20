package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.AuthorizationAuditLog;

import java.time.LocalDateTime;

public class AuthorizationAuditResponseDTO {

    private Long id;
    private Long userId;
    private String email;
    private String action;
    private String resource;
    private String permissionRequired;
    private String decision;
    private String reason;
    private String ipAddress;
    private LocalDateTime timestamp;

    public static AuthorizationAuditResponseDTO fromEntity(AuthorizationAuditLog entity) {
        if (entity == null) {
            return null;
        }
        AuthorizationAuditResponseDTO dto = new AuthorizationAuditResponseDTO();
        dto.setId(entity.getId());
        if (entity.getUser() != null) {
            dto.setUserId(entity.getUser().getId());
            dto.setEmail(entity.getUser().getEmail());
        } else {
            dto.setEmail(entity.getEmail());
        }
        dto.setAction(entity.getAction());
        dto.setResource(entity.getResource());
        dto.setPermissionRequired(entity.getPermissionRequired());
        dto.setDecision(entity.getDecision());
        dto.setReason(entity.getReason());
        dto.setIpAddress(entity.getIpAddress());
        dto.setTimestamp(entity.getTimestamp());
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getPermissionRequired() {
        return permissionRequired;
    }

    public void setPermissionRequired(String permissionRequired) {
        this.permissionRequired = permissionRequired;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
