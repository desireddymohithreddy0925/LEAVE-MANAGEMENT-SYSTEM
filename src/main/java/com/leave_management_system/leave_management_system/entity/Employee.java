package com.leave_management_system.leave_management_system.entity;
// "Defines the package where the Employee entity class belongs."

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
// "Imports JPA annotations used to map this Java class to a database table."

@Entity
// "Marks this class as a JPA entity, so Hibernate manages it and maps it to a database table."

@Table(name = "employees")
// "Specifies that this entity should be mapped to the 'employees' table."

public class Employee {

    @Id
    // "Marks 'id' as the primary key of the employees table."

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // "Automatically generates the employee ID using the database's auto-increment mechanism."

    private Long id;
    // "Stores the unique ID of the employee."

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Column(unique = true, nullable = false)
    // "Makes the email unique and prevents the database from storing a NULL email."

    private String email;
    // "Stores the employee's email address."

    private String phone;
    // "Stores the employee's phone number."

    private boolean active;
    // "Stores whether the employee is currently active or inactive."

    @ManyToOne
    // "Defines a many-to-one relationship where many employees can belong to one department."

    @JoinColumn(name = "department_id")
    // "Creates the department_id foreign-key column in the employees table."

    private Department department;
    // "Stores the department associated with this employee."


    public Employee() {
    }
    // "Default constructor required by JPA/Hibernate to create Employee objects."


    public Long getId() {
        return id;
    }
    // "Returns the employee's ID."

    public void setId(Long id) {
        this.id = id;
    }
    // "Sets the employee's ID."


    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }


    public String getEmail() {
        return email;
    }
    // "Returns the employee's email."

    public void setEmail(String email) {
        this.email = email;
    }
    // "Sets the employee's email."


    public String getPhone() {
        return phone;
    }
    // "Returns the employee's phone number."

    public void setPhone(String phone) {
        this.phone = phone;
    }
    // "Sets the employee's phone number."


    public boolean isActive() {
        return active;
    }
    // "Returns true if the employee is active and false if inactive."

    public void setActive(boolean active) {
        this.active = active;
    }
    // "Sets the employee's active/inactive status."


    public Department getDepartment() {
        return department;
    }
    // "Returns the department associated with the employee."

    public void setDepartment(Department department) {
        this.department = department;
    }
    // "Assigns a department to the employee."
}