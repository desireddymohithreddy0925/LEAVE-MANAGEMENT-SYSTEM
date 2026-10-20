package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;

public class AnonymizeResponseDTO {

    private Long employeeId;
    private LocalDateTime anonymizedAt;
    private String status;
    private String message;

    public AnonymizeResponseDTO() {}

    public AnonymizeResponseDTO(Long employeeId, LocalDateTime anonymizedAt, String status, String message) {
        this.employeeId = employeeId;
        this.anonymizedAt = anonymizedAt;
        this.status = status;
        this.message = message;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDateTime getAnonymizedAt() {
        return anonymizedAt;
    }

    public void setAnonymizedAt(LocalDateTime anonymizedAt) {
        this.anonymizedAt = anonymizedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
