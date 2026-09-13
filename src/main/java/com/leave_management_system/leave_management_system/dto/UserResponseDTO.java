package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;

import java.util.Set;
import java.util.stream.Collectors;

public class UserResponseDTO {

    private Long id;
    private String email;
    private boolean active;
    private Set<RoleResponseDTO> roles;

    public static UserResponseDTO fromEntity(User user) {
        if (user == null) {
            return null;
        }
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setActive(user.isActive());
        if (user.getRoles() != null) {
            dto.setRoles(user.getRoles().stream()
                    .map(RoleResponseDTO::fromEntity)
                    .collect(Collectors.toSet()));
        }
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<RoleResponseDTO> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleResponseDTO> roles) {
        this.roles = roles;
    }
}
