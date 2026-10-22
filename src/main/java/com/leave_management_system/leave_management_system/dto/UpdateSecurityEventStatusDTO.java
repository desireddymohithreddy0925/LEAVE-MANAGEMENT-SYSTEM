package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.SecurityEventStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateSecurityEventStatusDTO {

    @NotNull(message = "Status is required")
    private SecurityEventStatus status;

    @Size(max = 2000, message = "Resolution notes cannot exceed 2000 characters")
    private String resolutionNotes;

    public UpdateSecurityEventStatusDTO() {
    }

    public UpdateSecurityEventStatusDTO(SecurityEventStatus status, String resolutionNotes) {
        this.status = status;
        this.resolutionNotes = resolutionNotes;
    }

    public SecurityEventStatus getStatus() {
        return status;
    }

    public void setStatus(SecurityEventStatus status) {
        this.status = status;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }
}
