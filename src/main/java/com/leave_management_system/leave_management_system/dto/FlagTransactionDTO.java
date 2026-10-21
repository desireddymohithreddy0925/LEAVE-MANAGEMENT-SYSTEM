package com.leave_management_system.leave_management_system.dto;

import jakarta.validation.constraints.NotBlank;

public class FlagTransactionDTO {

    @NotBlank(message = "Flagging reason is required")
    private String reason;

    private String notes;

    public FlagTransactionDTO() {
    }

    public FlagTransactionDTO(String reason, String notes) {
        this.reason = reason;
        this.notes = notes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
