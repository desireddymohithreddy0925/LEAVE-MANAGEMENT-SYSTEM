package com.leave_management_system.leave_management_system.dto;

import java.util.ArrayList;
import java.util.List;

public class EmployeeHierarchyDTO {
    private EmployeeResponseDTO employee;
    private List<EmployeeResponseDTO> reportingChain = new ArrayList<>(); // Management chain upwards
    private List<EmployeeResponseDTO> directReports = new ArrayList<>(); // Direct subordinates downwards

    public EmployeeHierarchyDTO() {
    }

    public EmployeeHierarchyDTO(EmployeeResponseDTO employee, List<EmployeeResponseDTO> reportingChain, List<EmployeeResponseDTO> directReports) {
        this.employee = employee;
        this.reportingChain = reportingChain != null ? reportingChain : new ArrayList<>();
        this.directReports = directReports != null ? directReports : new ArrayList<>();
    }

    public EmployeeResponseDTO getEmployee() {
        return employee;
    }

    public void setEmployee(EmployeeResponseDTO employee) {
        this.employee = employee;
    }

    public List<EmployeeResponseDTO> getReportingChain() {
        return reportingChain;
    }

    public void setReportingChain(List<EmployeeResponseDTO> reportingChain) {
        this.reportingChain = reportingChain;
    }

    public List<EmployeeResponseDTO> getDirectReports() {
        return directReports;
    }

    public void setDirectReports(List<EmployeeResponseDTO> directReports) {
        this.directReports = directReports;
    }
}
