package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.LeaveType;

public class LeaveTypeResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Integer defaultDays;
    private boolean active;

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
