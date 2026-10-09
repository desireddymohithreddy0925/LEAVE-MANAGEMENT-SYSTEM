package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.Permission;

public class PermissionResponseDTO {
    private Long id;
    private String name;
    private String description;
    private boolean active;

    public PermissionResponseDTO() {
    }

    public PermissionResponseDTO(Long id, String name, String description, boolean active) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public static PermissionResponseDTO fromEntity(Permission permission) {
        return new PermissionResponseDTO(
            permission.getId(),
            permission.getName(),
            permission.getDescription(),
            permission.isActive()
        );
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
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
    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
}
