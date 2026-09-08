package com.leave_management_system.leave_management_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectRequestDTO {

    @NotBlank(message = "Rejection reason is mandatory")
    @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
