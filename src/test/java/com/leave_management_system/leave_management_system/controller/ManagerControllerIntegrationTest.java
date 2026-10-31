package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ManagerControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String adminToken;
    private String manager1Token;
    private String manager2Token;
    private String regularEmpToken;
    private String unlinkedManagerToken;

    private Employee manager1Emp;
    private Employee manager2Emp;
    private Employee leadEmp;
    private Employee engineerEmp;
    private Department dept1;
    private Department dept2;
    private LeaveType leaveType;

    @BeforeEach
    public void setup() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Ensure permissions exist
        Permission teamViewPerm = permissionRepository.findByName("LEAVE_VIEW_TEAM").orElseGet(() ->
                permissionRepository.save(new Permission("LEAVE_VIEW_TEAM", "View team members and leave requests")));
        Permission empViewPerm = permissionRepository.findByName("EMPLOYEE_VIEW").orElseGet(() ->
                permissionRepository.save(new Permission("EMPLOYEE_VIEW", "View employee profiles")));
        Permission leaveCreatePerm = permissionRepository.findByName("LEAVE_CREATE").orElseGet(() ->
                permissionRepository.save(new Permission("LEAVE_CREATE", "Create leave request")));

        // 1. Roles
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        adminRole.getPermissions().add(empViewPerm);
        adminRole.getPermissions().add(teamViewPerm);
        roleRepository.save(adminRole);

        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        managerRole.getPermissions().add(teamViewPerm);
        managerRole.getPermissions().add(empViewPerm);
        roleRepository.save(managerRole);

        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        employeeRole.getPermissions().add(leaveCreatePerm);
        roleRepository.save(employeeRole);

        // 2. Admin User
        User adminUser = createUser("admin.mgr." + System.currentTimeMillis() + "@test.com", adminRole);
        adminToken = jwtUtils.generateTokenFromUsername(adminUser.getEmail());

        // 3. Departments
        dept1 = new Department();
        dept1.setName("Engineering " + System.currentTimeMillis());
        dept1 = departmentRepository.save(dept1);

        dept2 = new Department();
        dept2.setName("Finance " + System.currentTimeMillis());
        dept2 = departmentRepository.save(dept2);

        // 4. Manager 1
        User m1User = createUser("m1.mgr." + System.currentTimeMillis() + "@test.com", managerRole);
        manager1Token = jwtUtils.generateTokenFromUsername(m1User.getEmail());
        manager1Emp = createEmployeeRecord(m1User, dept1, null, "M1");
        dept1.setManager(manager1Emp);
        dept1 = departmentRepository.save(dept1);

        // 5. Manager 2
        User m2User = createUser("m2.mgr." + System.currentTimeMillis() + "@test.com", managerRole);
        manager2Token = jwtUtils.generateTokenFromUsername(m2User.getEmail());
        manager2Emp = createEmployeeRecord(m2User, dept2, null, "M2");
        dept2.setManager(manager2Emp);
        dept2 = departmentRepository.save(dept2);

        // 6. Lead (subordinate to Manager 1)
        User leadUser = createUser("lead.mgr." + System.currentTimeMillis() + "@test.com", employeeRole);
        leadEmp = createEmployeeRecord(leadUser, dept1, manager1Emp, "LEAD");

        // 7. Engineer (subordinate to Lead)
        User engUser = createUser("eng.mgr." + System.currentTimeMillis() + "@test.com", employeeRole);
        regularEmpToken = jwtUtils.generateTokenFromUsername(engUser.getEmail());
        engineerEmp = createEmployeeRecord(engUser, dept1, leadEmp, "ENG");

        // 8. User with LEAVE_VIEW_TEAM but NOT linked to an employee record
        User unlinkedUser = createUser("unlinked.mgr." + System.currentTimeMillis() + "@test.com", managerRole);
        unlinkedManagerToken = jwtUtils.generateTokenFromUsername(unlinkedUser.getEmail());

        // 9. Leave Type & Leave Balance for Subordinate (Lead)
        leaveType = new LeaveType();
        leaveType.setName("ManagerTest Vacation " + System.currentTimeMillis());
        leaveType.setDefaultDays(20);
        leaveType.setActive(true);
        leaveType = leaveTypeRepository.save(leaveType);

        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(leadEmp);
        balance.setLeaveType(leaveType);
        balance.setYear(LocalDate.now().getYear());
        balance.setTotal(20);
        balance.setUsed(0);
        balance.setAvailable(20);
        leaveBalanceRepository.save(balance);
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password123!"));
        user.setActive(true);
        user.setAccountNonLocked(true);
        user.setRoles(new HashSet<>(Set.of(role)));
        return userRepository.save(user);
    }

    private Employee createEmployeeRecord(User user, Department dept, Employee manager, String prefix) {
        Employee emp = new Employee();
        emp.setUser(user);
        emp.setFirstName(prefix + "First");
        emp.setLastName("Last");
        emp.setDepartment(dept);
        emp.setManager(manager);
        emp.setEmployeeCode("CODE-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000));
        emp.setStatus("ACTIVE");
        emp.setSalary(new BigDecimal("75000"));
        emp.setPhone("1234567890");
        return employeeRepository.save(emp);
    }

    private LocalDate getNextWeekday(LocalDate from) {
        LocalDate date = from;
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    @Test
    void getMyTeam_Success() throws Exception {
        mockMvc.perform(get("/api/managers/me/team")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].id", hasItem(leadEmp.getId().intValue())));
    }

    @Test
    void getMyTeam_NotLinkedToEmployee_ReturnsConflict() throws Exception {
        mockMvc.perform(get("/api/managers/me/team")
                .header("Authorization", "Bearer " + unlinkedManagerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void getMyTeam_Forbidden_NoAuthority() throws Exception {
        mockMvc.perform(get("/api/managers/me/team")
                .header("Authorization", "Bearer " + regularEmpToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyTeam_Unauthenticated_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/managers/me/team"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyTeamHierarchy_Success() throws Exception {
        mockMvc.perform(get("/api/managers/me/team-hierarchy")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(manager1Emp.getId()))
                .andExpect(jsonPath("$.directReports", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.directReports[0].id").value(leadEmp.getId()))
                .andExpect(jsonPath("$.subordinateTeams", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.subordinateTeams[0].managerId").value(leadEmp.getId()));
    }

    @Test
    void getMyTeamHierarchy_AlternativeEndpoint_Success() throws Exception {
        mockMvc.perform(get("/api/managers/me/hierarchy")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(manager1Emp.getId()));
    }

    @Test
    void getManagerTeamById_OwnTeam_Success() throws Exception {
        mockMvc.perform(get("/api/managers/" + manager1Emp.getId() + "/team")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].id", hasItem(leadEmp.getId().intValue())));
    }

    @Test
    void getManagerTeamById_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/managers/" + manager1Emp.getId() + "/team")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].id", hasItem(leadEmp.getId().intValue())));
    }

    @Test
    void getManagerTeamById_OtherManager_Forbidden() throws Exception {
        mockMvc.perform(get("/api/managers/" + manager2Emp.getId() + "/team")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void getManagerTeamById_NonExistentManager_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/managers/999999/team")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getManagerHierarchyById_OwnHierarchy_Success() throws Exception {
        mockMvc.perform(get("/api/managers/" + manager1Emp.getId() + "/team-hierarchy")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(manager1Emp.getId()))
                .andExpect(jsonPath("$.directReports", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void getManagerHierarchyById_Admin_Success() throws Exception {
        mockMvc.perform(get("/api/managers/" + manager1Emp.getId() + "/hierarchy")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(manager1Emp.getId()));
    }

    @Test
    void getManagerHierarchyById_OtherManager_Forbidden() throws Exception {
        mockMvc.perform(get("/api/managers/" + manager2Emp.getId() + "/team-hierarchy")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void getManagerHierarchyById_NotFound() throws Exception {
        mockMvc.perform(get("/api/managers/999999/team-hierarchy")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyTeamLeaveRequests_Success() throws Exception {
        // Subordinate (leadEmp) submits a leave request
        String leadToken = jwtUtils.generateTokenFromUsername(leadEmp.getUser().getEmail());
        LocalDate start = getNextWeekday(LocalDate.now().plusDays(20));
        LocalDate end = getNextWeekday(start.plusDays(1));

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveType.getId());
        reqDto.setStartDate(start);
        reqDto.setEndDate(end);
        reqDto.setReason("Team holiday request");

        mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + leadToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated());

        // Manager 1 views team leave requests
        mockMvc.perform(get("/api/managers/me/leave-requests")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].employee.id", hasItem(leadEmp.getId().intValue())));
    }

    @Test
    void getMyTeamLeaveRequests_Forbidden_WhenMissingAuthority() throws Exception {
        mockMvc.perform(get("/api/managers/me/leave-requests")
                .header("Authorization", "Bearer " + regularEmpToken))
                .andExpect(status().isForbidden());
    }
}
