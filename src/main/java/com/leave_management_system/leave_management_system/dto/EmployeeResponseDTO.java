package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.Employee;

public class EmployeeResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String employeeCode;
    private String email;
    private String phone;
    private String status;
    private java.math.BigDecimal salary;
    private DepartmentResponseDTO department;

    public static EmployeeResponseDTO fromEntity(Employee employee) {
        if (employee == null) return null;
        EmployeeResponseDTO dto = new EmployeeResponseDTO();
        dto.setId(employee.getId());
        dto.setFirstName(employee.getFirstName());
        dto.setLastName(employee.getLastName());
        dto.setEmployeeCode(employee.getEmployeeCode());
        if (employee.getUser() != null) {
            dto.setEmail(employee.getUser().getEmail());
        }
        dto.setPhone(employee.getPhone());
        dto.setStatus(employee.getStatus());
        dto.setSalary(employee.getSalary());
        if (employee.getDepartment() != null) {
            dto.setDepartment(DepartmentResponseDTO.fromEntity(employee.getDepartment()));
        }
        return dto;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public java.math.BigDecimal getSalary() { return salary; }
    public void setSalary(java.math.BigDecimal salary) { this.salary = salary; }
    public DepartmentResponseDTO getDepartment() { return department; }
    public void setDepartment(DepartmentResponseDTO department) { this.department = department; }
}
