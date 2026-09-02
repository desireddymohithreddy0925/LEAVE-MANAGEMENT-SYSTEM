package com.leave_management_system.leave_management_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// Tells Spring Boot that this is the main application class
// and automatically enables component scanning and configuration.

public class LeaveManagementSystemApplication {

    public static void main(String[] args) {

        SpringApplication.run(LeaveManagementSystemApplication.class, args);
    }
}