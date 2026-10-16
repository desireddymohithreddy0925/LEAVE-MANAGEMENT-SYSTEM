package com.leave_management_system.leave_management_system.dto;

import java.util.List;

public class OverlapCheckResponseDTO {

    private boolean overlapping;
    private String message;
    private List<LeaveResponseDTO> conflictingRequests;

    public OverlapCheckResponseDTO() {
    }

    public OverlapCheckResponseDTO(boolean overlapping, String message, List<LeaveResponseDTO> conflictingRequests) {
        this.overlapping = overlapping;
        this.message = message;
        this.conflictingRequests = conflictingRequests;
    }

    public boolean isOverlapping() {
        return overlapping;
    }

    public void setOverlapping(boolean overlapping) {
        this.overlapping = overlapping;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<LeaveResponseDTO> getConflictingRequests() {
        return conflictingRequests;
    }

    public void setConflictingRequests(List<LeaveResponseDTO> conflictingRequests) {
        this.conflictingRequests = conflictingRequests;
    }
}
