package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.Department;

public class DepartmentResponseDTO {
    private Long id;
    private String name;
    private Long managerId;
    private String managerName;

    public DepartmentResponseDTO() {}

    public DepartmentResponseDTO(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public DepartmentResponseDTO(Long id, String name, Long managerId, String managerName) {
        this.id = id;
        this.name = name;
        this.managerId = managerId;
        this.managerName = managerName;
    }

    public static DepartmentResponseDTO fromEntity(Department department) {
        if (department == null) return null;
        DepartmentResponseDTO dto = new DepartmentResponseDTO(department.getId(), department.getName());
        if (department.getManager() != null) {
            dto.setManagerId(department.getManager().getId());
            dto.setManagerName(department.getManager().getFirstName() + " " + department.getManager().getLastName());
        }
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

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }
}
