package com.leave_management_system.leave_management_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;

@Entity
@Table(
    name = "leave_balances",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {"employee_id", "leave_type_id", "year"}
        )
    }
)
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer total;

    @Column(nullable = false)
    private Integer used;

    @Min(value = 0, message = "Available days cannot be negative")
    @Column(nullable = false)
    private Integer available;

    public LeaveBalance() {
    }

    public LeaveBalance(
            Employee employee,
            LeaveType leaveType,
            Integer year,
            Integer total,
            Integer used,
            Integer available) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.year = year;
        this.total = total;
        this.used = used;
        this.available = available;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getUsed() {
        return used;
    }

    public void setUsed(Integer used) {
        this.used = used;
    }

    public Integer getAvailable() {
        return available;
    }

    public void setAvailable(Integer available) {
        this.available = available;
    }
}
