package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.ReviewStatus;
import jakarta.validation.constraints.NotBlank;

public class ReviewDecisionDTO {

    private ReviewStatus decision;

    @NotBlank(message = "Review notes are required")
    private String notes;

    private String resolution;

    public ReviewDecisionDTO() {
    }

    public ReviewDecisionDTO(ReviewStatus decision, String notes, String resolution) {
        this.decision = decision;
        this.notes = notes;
        this.resolution = resolution;
    }

    public ReviewStatus getDecision() {
        return decision;
    }

    public void setDecision(ReviewStatus decision) {
        this.decision = decision;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
}
