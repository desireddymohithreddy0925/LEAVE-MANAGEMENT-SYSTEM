package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.Department;

public class DepartmentResponseDTO {
    private Long id;
    private String name;

    public DepartmentResponseDTO() {}

    public DepartmentResponseDTO(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public static DepartmentResponseDTO fromEntity(Department department) {
        if (department == null) return null;
        return new DepartmentResponseDTO(department.getId(), department.getName());
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
