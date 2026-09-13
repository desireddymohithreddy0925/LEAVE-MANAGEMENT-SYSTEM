package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.Role;

public class RoleResponseDTO {

    private Long id;
    private String name;

    public static RoleResponseDTO fromEntity(Role role) {
        if (role == null) {
            return null;
        }
        RoleResponseDTO dto = new RoleResponseDTO();
        dto.setId(role.getId());
        dto.setName(role.getName());
        return dto;
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
}
