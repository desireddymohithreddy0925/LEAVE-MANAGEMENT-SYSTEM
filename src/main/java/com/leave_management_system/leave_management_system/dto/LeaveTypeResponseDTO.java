package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.LeaveType;

public class LeaveTypeResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Integer defaultDays;
    private boolean active;

    public LeaveTypeResponseDTO() {
    }

    public LeaveTypeResponseDTO(Long id, String name, String description, Integer defaultDays, boolean active) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.defaultDays = defaultDays;
        this.active = active;
    }

    public static LeaveTypeResponseDTO fromEntity(LeaveType leaveType) {
        if (leaveType == null) return null;
        LeaveTypeResponseDTO dto = new LeaveTypeResponseDTO();
        dto.setId(leaveType.getId());
        dto.setName(leaveType.getName());
        dto.setDescription(leaveType.getDescription());
        dto.setDefaultDays(leaveType.getDefaultDays());
        dto.setActive(leaveType.isActive());
        return dto;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getDefaultDays() { return defaultDays; }
    public void setDefaultDays(Integer defaultDays) { this.defaultDays = defaultDays; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
