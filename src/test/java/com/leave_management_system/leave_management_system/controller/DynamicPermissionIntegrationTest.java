package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DynamicPermissionIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private LeaveBalanceRepository leaveBalanceRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private JwtUtils jwtUtils;

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String adminToken;
    private String managerToken;
    private Role managerRole;
    private LeaveRequest pendingLeaveRequest1;
    private LeaveRequest pendingLeaveRequest2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Ensure we have clean state in the test for users
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Get Roles
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        managerRole = roleRepository.findByName("MANAGER").orElseThrow();

        // 2. Setup Admin User & Token
        User adminUser = new User();
        adminUser.setEmail("dynamic.admin@test.com");
        adminUser.setPassword("password");
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        userRepository.save(adminUser);

        List<SimpleGrantedAuthority> adminAuthorities = adminRole.getPermissions().stream()
                .map(p -> new SimpleGrantedAuthority(p.getName()))
                .collect(Collectors.toList());
        adminAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), adminUser.getPassword(), adminAuthorities);
        adminToken = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities()));

        // 3. Setup Manager User & Token
        User managerUser = new User();
        managerUser.setEmail("dynamic.manager@test.com");
        managerUser.setPassword("password");
        managerUser.setActive(true);
        managerUser.getRoles().add(managerRole);
        userRepository.save(managerUser);

        Employee managerEmp = new Employee();
        managerEmp.setFirstName("Man");
        managerEmp.setLastName("Ager");
        managerEmp.setEmployeeCode("M001");
        managerEmp.setPhone("1234567890");
        managerEmp.setStatus("ACTIVE");
        managerEmp.setSalary(new BigDecimal("50000.00"));
        managerEmp.setUser(managerUser);
        managerEmp = employeeRepository.save(managerEmp);

        Department dept = new Department();
        dept.setName("IT Department");
        dept.setManager(managerEmp);
        dept = departmentRepository.save(dept);

        List<SimpleGrantedAuthority> managerAuthorities = managerRole.getPermissions().stream()
                .map(p -> new SimpleGrantedAuthority(p.getName()))
                .collect(Collectors.toList());
        managerAuthorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));

        UserDetails managerDetails = new org.springframework.security.core.userdetails.User(
                managerUser.getEmail(), managerUser.getPassword(), managerAuthorities);
        managerToken = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(managerDetails, null, managerDetails.getAuthorities()));

        // 4. Setup Employee (under manager)
        Employee emp = new Employee();
        emp.setFirstName("Emp");
        emp.setLastName("Loyee");
        emp.setEmployeeCode("E001");
        emp.setPhone("0987654321");
        emp.setStatus("ACTIVE");
        emp.setSalary(new BigDecimal("40000.00"));
        emp.setDepartment(dept);
        emp = employeeRepository.save(emp);

        // 5. Setup Leave Type & Balance
        LeaveType lt = new LeaveType();
        lt.setName("Annual Leave");
        lt.setDefaultDays(20);
        lt.setActive(true);
        lt = leaveTypeRepository.save(lt);

        LeaveBalance lb = new LeaveBalance();
        lb.setEmployee(emp);
        lb.setLeaveType(lt);
        lb.setYear(LocalDate.now().getYear());
        lb.setTotal(20);
        lb.setUsed(0);
        lb.setAvailable(20);
        leaveBalanceRepository.save(lb);

        // 6. Setup 2 Pending Leave Requests
        LeaveRequest lr1 = new LeaveRequest();
        lr1.setEmployee(emp);
        lr1.setLeaveType(lt);
        lr1.setStartDate(LocalDate.now().plusDays(1));
        lr1.setEndDate(LocalDate.now().plusDays(2));
        lr1.setStatus(LeaveStatus.PENDING);
        pendingLeaveRequest1 = leaveRequestRepository.save(lr1);

        LeaveRequest lr2 = new LeaveRequest();
        lr2.setEmployee(emp);
        lr2.setLeaveType(lt);
        lr2.setStartDate(LocalDate.now().plusDays(5));
        lr2.setEndDate(LocalDate.now().plusDays(6));
        lr2.setStatus(LeaveStatus.PENDING);
        pendingLeaveRequest2 = leaveRequestRepository.save(lr2);
    }

    @Test
    public void testDynamicPermissionSeparationForManager() throws Exception {
        // Ensure manager has LEAVE_APPROVE to start with.
        // If they don't, add it.
        boolean hasApprove = managerRole.getPermissions().stream().anyMatch(p -> p.getName().equals("LEAVE_APPROVE"));
        if (!hasApprove) {
             Permission approvePermission = permissionRepository.findByName("LEAVE_APPROVE").orElseThrow();
             managerRole.getPermissions().add(approvePermission);
             roleRepository.save(managerRole);
        }

        // 1. Remove LEAVE_APPROVE from MANAGER role as Admin
        List<Long> updatedPermissionIds = managerRole.getPermissions().stream()
                .filter(p -> !p.getName().equals("LEAVE_APPROVE"))
                .map(Permission::getId)
                .collect(Collectors.toList());
        System.out.println("TEST DEBUG: updatedPermissionIds = " + updatedPermissionIds);

        mockMvc.perform(put("/api/roles/" + managerRole.getId() + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedPermissionIds)))
                .andExpect(status().isOk());

        // 2. Manager attempts approval -> 403 Forbidden
        mockMvc.perform(put("/api/leave-requests/" + pendingLeaveRequest1.getId() + "/approve")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        // 3. Manager attempts rejection -> 200 OK (they still have LEAVE_REJECT)
        mockMvc.perform(put("/api/leave-requests/" + pendingLeaveRequest1.getId() + "/reject")
                .header("Authorization", "Bearer " + managerToken)
                .param("reason", "Not a good time"))
                .andExpect(status().isOk());

        // 4. Restore LEAVE_APPROVE to MANAGER role as Admin
        Permission approvePermission = permissionRepository.findByName("LEAVE_APPROVE").orElseThrow();
        updatedPermissionIds.add(approvePermission.getId());

        mockMvc.perform(put("/api/roles/" + managerRole.getId() + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedPermissionIds)))
                .andExpect(status().isOk());

        // 5. Manager attempts approval again -> 200 OK
        mockMvc.perform(put("/api/leave-requests/" + pendingLeaveRequest2.getId() + "/approve")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }
}
