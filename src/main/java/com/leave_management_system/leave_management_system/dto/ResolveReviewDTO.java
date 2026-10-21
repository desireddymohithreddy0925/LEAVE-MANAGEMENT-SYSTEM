package com.leave_management_system.leave_management_system.dto;

import jakarta.validation.constraints.NotBlank;

public class ResolveReviewDTO {

    @NotBlank(message = "Resolution is required")
    private String resolution;

    @NotBlank(message = "Resolution notes are required")
    private String notes;

    public ResolveReviewDTO() {
    }

    public ResolveReviewDTO(String resolution, String notes) {
        this.resolution = resolution;
        this.notes = notes;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
