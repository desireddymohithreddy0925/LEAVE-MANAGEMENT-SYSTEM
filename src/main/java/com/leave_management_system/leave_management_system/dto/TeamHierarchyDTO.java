package com.leave_management_system.leave_management_system.dto;

import java.util.ArrayList;
import java.util.List;

public class TeamHierarchyDTO {
    private Long managerId;
    private String managerName;
    private String managerEmail;
    private String departmentName;
    private List<EmployeeResponseDTO> directReports = new ArrayList<>();
    private List<TeamHierarchyDTO> subordinateTeams = new ArrayList<>();
    private int totalTeamSize;

    public TeamHierarchyDTO() {
    }

    public TeamHierarchyDTO(Long managerId, String managerName, String managerEmail, String departmentName,
                            List<EmployeeResponseDTO> directReports, List<TeamHierarchyDTO> subordinateTeams,
                            int totalTeamSize) {
        this.managerId = managerId;
        this.managerName = managerName;
        this.managerEmail = managerEmail;
        this.departmentName = departmentName;
        this.directReports = directReports != null ? directReports : new ArrayList<>();
        this.subordinateTeams = subordinateTeams != null ? subordinateTeams : new ArrayList<>();
        this.totalTeamSize = totalTeamSize;
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

    public String getManagerEmail() {
        return managerEmail;
    }

    public void setManagerEmail(String managerEmail) {
        this.managerEmail = managerEmail;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public List<EmployeeResponseDTO> getDirectReports() {
        return directReports;
    }

    public void setDirectReports(List<EmployeeResponseDTO> directReports) {
        this.directReports = directReports;
    }

    public List<TeamHierarchyDTO> getSubordinateTeams() {
        return subordinateTeams;
    }

    public void setSubordinateTeams(List<TeamHierarchyDTO> subordinateTeams) {
        this.subordinateTeams = subordinateTeams;
    }

    public int getTotalTeamSize() {
        return totalTeamSize;
    }

    public void setTotalTeamSize(int totalTeamSize) {
        this.totalTeamSize = totalTeamSize;
    }
}
