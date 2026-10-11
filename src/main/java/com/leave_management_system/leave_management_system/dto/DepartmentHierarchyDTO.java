package com.leave_management_system.leave_management_system.dto;

import java.util.ArrayList;
import java.util.List;

public class DepartmentHierarchyDTO {
    private Long departmentId;
    private String departmentName;
    private EmployeeResponseDTO manager;
    private List<EmployeeResponseDTO> employees = new ArrayList<>();
    private List<TeamHierarchyDTO> teams = new ArrayList<>();
    private int totalEmployees;

    public DepartmentHierarchyDTO() {
    }

    public DepartmentHierarchyDTO(Long departmentId, String departmentName, EmployeeResponseDTO manager,
                                  List<EmployeeResponseDTO> employees, List<TeamHierarchyDTO> teams, int totalEmployees) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.manager = manager;
        this.employees = employees != null ? employees : new ArrayList<>();
        this.teams = teams != null ? teams : new ArrayList<>();
        this.totalEmployees = totalEmployees;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public EmployeeResponseDTO getManager() {
        return manager;
    }

    public void setManager(EmployeeResponseDTO manager) {
        this.manager = manager;
    }

    public List<EmployeeResponseDTO> getEmployees() {
        return employees;
    }

    public void setEmployees(List<EmployeeResponseDTO> employees) {
        this.employees = employees;
    }

    public List<TeamHierarchyDTO> getTeams() {
        return teams;
    }

    public void setTeams(List<TeamHierarchyDTO> teams) {
        this.teams = teams;
    }

    public int getTotalEmployees() {
        return totalEmployees;
    }

    public void setTotalEmployees(int totalEmployees) {
        this.totalEmployees = totalEmployees;
    }
}
