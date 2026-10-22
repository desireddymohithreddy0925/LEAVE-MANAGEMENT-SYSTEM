package com.leave_management_system.leave_management_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_events")
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private SecurityEventSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SecurityEventStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "email")
    private String email;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "resource")
    private String resource;

    @Column(name = "threat_score", nullable = false)
    private Integer threatScore = 0;

    @Column(name = "threat_reason", length = 500)
    private String threatReason;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "details", length = 4000)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SecurityEvent() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (threatScore == null) {
            threatScore = 0;
        }
        if (status == null) {
            status = SecurityEventStatus.OPEN;
        }
        if (severity == null) {
            severity = SecurityEventSeverity.LOW;
        }
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public User getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(User resolvedBy) {
        this.resolvedBy = resolvedBy;
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
