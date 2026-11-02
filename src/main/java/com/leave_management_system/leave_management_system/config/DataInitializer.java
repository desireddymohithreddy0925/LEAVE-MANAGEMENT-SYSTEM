package com.leave_management_system.leave_management_system.config;

import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           RoleRepository roleRepository,
                           EmployeeRepository employeeRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String adminEmail = "admin@leavemanagement.com";
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            log.info("Initializing default administrative account: {}", adminEmail);

            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseGet(() -> roleRepository.save(new Role("ADMIN")));
            Role managerRole = roleRepository.findByName("MANAGER")
                    .orElseGet(() -> roleRepository.save(new Role("MANAGER")));
            Role hrRole = roleRepository.findByName("HR")
                    .orElseGet(() -> roleRepository.save(new Role("HR")));
            Role employeeRole = roleRepository.findByName("EMPLOYEE")
                    .orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

            User adminUser = new User();
            adminUser.setEmail(adminEmail);
            adminUser.setPassword(passwordEncoder.encode("Admin@123"));
            adminUser.setActive(true);
            adminUser.setAccountNonLocked(true);
            adminUser.setRoles(new HashSet<>(Set.of(adminRole, managerRole, hrRole, employeeRole)));
            adminUser = userRepository.save(adminUser);

            Employee adminEmployee = new Employee();
            adminEmployee.setFirstName("System");
            adminEmployee.setLastName("Administrator");
            adminEmployee.setEmployeeCode("ADM-001");
            adminEmployee.setPhone("1234567890");
            adminEmployee.setStatus("ACTIVE");
            adminEmployee.setUser(adminUser);
            employeeRepository.save(adminEmployee);

            log.info("Default administrative account initialized successfully.");
        }
    }
}

