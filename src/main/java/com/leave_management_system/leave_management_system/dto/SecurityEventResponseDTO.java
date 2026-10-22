package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.SecurityEventSeverity;
import com.leave_management_system.leave_management_system.entity.SecurityEventStatus;

import java.time.LocalDateTime;

public class SecurityEventResponseDTO {

    private Long id;
    private String eventType;
    private SecurityEventSeverity severity;
    private SecurityEventStatus status;
    private Long userId;
    private String userEmail;
    private String email;
    private String ipAddress;
    private String userAgent;
    private String resource;
    private Integer threatScore;
    private String threatReason;
    private String resolutionNotes;
    private Long resolvedById;
    private String resolvedByEmail;
    private LocalDateTime resolvedAt;
    private String details;
    private LocalDateTime createdAt;

    public SecurityEventResponseDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public SecurityEventSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(SecurityEventSeverity severity) {
        this.severity = severity;
    }

    public SecurityEventStatus getStatus() {
        return status;
    }

    public void setStatus(SecurityEventStatus status) {
        this.status = status;
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

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public Integer getThreatScore() {
        return threatScore;
    }

    public void setThreatScore(Integer threatScore) {
        this.threatScore = threatScore;
    }

    public String getThreatReason() {
        return threatReason;
    }

    public void setThreatReason(String threatReason) {
        this.threatReason = threatReason;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public Long getResolvedById() {
        return resolvedById;
    }

    public void setResolvedById(Long resolvedById) {
        this.resolvedById = resolvedById;
    }

    public String getResolvedByEmail() {
        return resolvedByEmail;
    }

    public void setResolvedByEmail(String resolvedByEmail) {
        this.resolvedByEmail = resolvedByEmail;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
