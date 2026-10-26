package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.LeaveRequestSignoff;
import java.time.LocalDateTime;

public class LeaveSignoffResponseDTO {

    private Long id;
    private String approverRole;
    private Long approverId;
    private String approverEmail;
    private String status;
    private String comments;
    private LocalDateTime signedAt;

    public LeaveSignoffResponseDTO() {
    }

    public static LeaveSignoffResponseDTO fromEntity(LeaveRequestSignoff signoff) {
        if (signoff == null) {
            return null;
        }
        LeaveSignoffResponseDTO dto = new LeaveSignoffResponseDTO();
        dto.setId(signoff.getId());
        dto.setApproverRole(signoff.getApproverRole());
        if (signoff.getApprover() != null) {
            dto.setApproverId(signoff.getApprover().getId());
            dto.setApproverEmail(signoff.getApprover().getEmail());
        }
        dto.setStatus(signoff.getStatus());
        dto.setComments(signoff.getComments());
        dto.setSignedAt(signoff.getSignedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApproverRole() {
        return approverRole;
    }

    public void setApproverRole(String approverRole) {
        this.approverRole = approverRole;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getApproverEmail() {
        return approverEmail;
    }

    public void setApproverEmail(String approverEmail) {
        this.approverEmail = approverEmail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public LocalDateTime getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(LocalDateTime signedAt) {
        this.signedAt = signedAt;
    }
}
