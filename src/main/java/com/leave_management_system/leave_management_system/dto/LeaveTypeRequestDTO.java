package com.leave_management_system.leave_management_system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class LeaveTypeRequestDTO {
    @NotBlank(message = "Leave type name is required")
    private String name;
    
    private String description;
    
    @Min(value = 1, message = "Default days must be at least 1")
    private Integer defaultDays;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDefaultDays() {
        return defaultDays;
    }

    public void setDefaultDays(Integer defaultDays) {
        this.defaultDays = defaultDays;
    }
}
