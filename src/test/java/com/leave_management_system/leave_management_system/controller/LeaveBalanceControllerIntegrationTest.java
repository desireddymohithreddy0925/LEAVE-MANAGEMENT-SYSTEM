package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
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
import java.time.Year;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LeaveBalanceControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private String employeeToken;
    private String otherEmployeeToken;
    private Employee testEmployee;
    private Employee otherEmployee;
    private LeaveType testLeaveType;
    private Department department;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Admin user and token
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        User adminUser = new User();
        adminUser.setEmail("bal-admin-" + suffix + "@test.com");
        adminUser.setPassword("password");
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        userRepository.save(adminUser);

        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(),
                adminUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        adminToken = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities())
        );

        // 2. Department
        department = new Department();
        department.setName("Bal Dept " + suffix);
        departmentRepository.save(department);

        // 3. Employee 1 user, entity and token
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        User empUser = new User();
        empUser.setEmail("bal-emp1-" + suffix + "@test.com");
        empUser.setPassword("password");
        empUser.setActive(true);
        empUser.getRoles().add(empRole);
        userRepository.save(empUser);

        UserDetails empDetails = new org.springframework.security.core.userdetails.User(
                empUser.getEmail(),
                empUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );
        employeeToken = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(empDetails, null, empDetails.getAuthorities())
        );

        testEmployee = new Employee();
        testEmployee.setFirstName("Bal");
        testEmployee.setLastName("One");
        testEmployee.setEmployeeCode("EMP-B1-" + suffix);
        testEmployee.setSalary(new BigDecimal("50000"));
        testEmployee.setStatus("ACTIVE");
        testEmployee.setDepartment(department);
        testEmployee.setUser(empUser);
        employeeRepository.save(testEmployee);

        // 4. Employee 2 user, entity and token
        User empUser2 = new User();
        empUser2.setEmail("bal-emp2-" + suffix + "@test.com");
        empUser2.setPassword("password");
        empUser2.setActive(true);
        empUser2.getRoles().add(empRole);
        userRepository.save(empUser2);

        UserDetails empDetails2 = new org.springframework.security.core.userdetails.User(
                empUser2.getEmail(),
                empUser2.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );
        otherEmployeeToken = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(empDetails2, null, empDetails2.getAuthorities())
        );

        otherEmployee = new Employee();
        otherEmployee.setFirstName("Bal");
        otherEmployee.setLastName("Two");
        otherEmployee.setEmployeeCode("EMP-B2-" + suffix);
        otherEmployee.setSalary(new BigDecimal("55000"));
        otherEmployee.setStatus("ACTIVE");
        otherEmployee.setDepartment(department);
        otherEmployee.setUser(empUser2);
        employeeRepository.save(otherEmployee);

        // 5. Active leave type
        testLeaveType = new LeaveType("Integration Balance Leave " + suffix, "Test", 12);
        testLeaveType.setActive(true);
        leaveTypeRepository.save(testLeaveType);
    }

    @Test
    void createLeaveBalance_Success() throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(testEmployee.getId());
        dto.setLeaveTypeId(testLeaveType.getId());

        mockMvc.perform(post("/api/leave-balances")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.available").value(12))
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.used").value(0))
                .andExpect(jsonPath("$.year").value(Year.now().getValue()))
                .andExpect(jsonPath("$.employee.id").value(testEmployee.getId()))
                .andExpect(jsonPath("$.leaveType.id").value(testLeaveType.getId()));
    }

    @Test
    void createLeaveBalance_WithExplicitTotal_Success() throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(testEmployee.getId());
        dto.setLeaveTypeId(testLeaveType.getId());
        dto.setTotal(25);

        mockMvc.perform(post("/api/leave-balances")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.available").value(25))
                .andExpect(jsonPath("$.used").value(0));
    }

    @Test
    void createLeaveBalance_Regression_InconsistentTotalAndAvailable_Returns400() throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(testEmployee.getId());
        dto.setLeaveTypeId(testLeaveType.getId());
        dto.setTotal(25);
        dto.setAvailable(20);

        mockMvc.perform(post("/api/leave-balances")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLeaveBalance_Regression_AvailableExceedsTotal_Returns400() throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(testEmployee.getId());
        dto.setLeaveTypeId(testLeaveType.getId());
        dto.setTotal(20);
        dto.setAvailable(25);

        mockMvc.perform(post("/api/leave-balances")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLeaveBalance_Duplicate() throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(testEmployee.getId());
        dto.setLeaveTypeId(testLeaveType.getId());

        mockMvc.perform(post("/api/leave-balances")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Second insert for same year/type/employee fails with 409
        mockMvc.perform(post("/api/leave-balances")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void createLeaveBalance_InactiveLeaveType_ThrowsBadRequest() throws Exception {
        LeaveType inactiveType = new LeaveType("Inactive Leave", "Desc", 10);
        inactiveType.setActive(false);
        leaveTypeRepository.save(inactiveType);

        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(testEmployee.getId());
        dto.setLeaveTypeId(inactiveType.getId());
        dto.setAvailable(10);

        mockMvc.perform(post("/api/leave-balances")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllLeaveBalances_Success() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        leaveBalanceRepository.save(balance);

        mockMvc.perform(get("/api/leave-balances")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getLeaveBalanceById_Success() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        balance = leaveBalanceRepository.save(balance);

        mockMvc.perform(get("/api/leave-balances/" + balance.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(balance.getId()))
                .andExpect(jsonPath("$.available").value(12))
                .andExpect(jsonPath("$.total").value(12));
    }

    @Test
    void getLeaveBalanceById_NotFound() throws Exception {
        mockMvc.perform(get("/api/leave-balances/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBalancesByEmployee_Success() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        leaveBalanceRepository.save(balance);

        mockMvc.perform(get("/api/leave-balances/employee/" + testEmployee.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employee.id").value(testEmployee.getId()));
    }

    @Test
    void getBalancesByEmployee_WithYearFilter() throws Exception {
        LeaveBalance bal2026 = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        leaveBalanceRepository.save(bal2026);

        LeaveBalance bal2027 = new LeaveBalance(testEmployee, testLeaveType, 2027, 14, 0, 14);
        leaveBalanceRepository.save(bal2027);

        // Filter 2026
        mockMvc.perform(get("/api/leave-balances/employee/" + testEmployee.getId() + "?year=2026")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].year").value(2026))
                .andExpect(jsonPath("$[0].total").value(12));

        // Filter 2027
        mockMvc.perform(get("/api/leave-balances/employee/" + testEmployee.getId() + "?year=2027")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].year").value(2027))
                .andExpect(jsonPath("$[0].total").value(14));
    }

    @Test
    void getMyBalances_Success() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        leaveBalanceRepository.save(balance);

        mockMvc.perform(get("/api/leave-balances/me")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employee.id").value(testEmployee.getId()));
    }

    @Test
    void getMyBalances_WithYearFilter() throws Exception {
        LeaveBalance bal2026 = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        leaveBalanceRepository.save(bal2026);

        LeaveBalance bal2027 = new LeaveBalance(testEmployee, testLeaveType, 2027, 15, 0, 15);
        leaveBalanceRepository.save(bal2027);

        mockMvc.perform(get("/api/leave-balances/me?year=2026")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].year").value(2026))
                .andExpect(jsonPath("$[0].total").value(12));
    }

    @Test
    void getMyBalances_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/leave-balances/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void initializeBalances_Success() throws Exception {
        LeaveType sickLeave = new LeaveType("Sick Leave Init", "Medical", 10);
        sickLeave.setActive(true);
        leaveTypeRepository.save(sickLeave);

        mockMvc.perform(post("/api/leave-balances/employee/" + testEmployee.getId() + "/initialize?year=2026")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Idempotent: re-calling initialize does not create duplicates
        mockMvc.perform(post("/api/leave-balances/employee/" + testEmployee.getId() + "/initialize?year=2026")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void updateLeaveBalance_Success() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        balance = leaveBalanceRepository.save(balance);

        LeaveBalanceRequestDTO updateDTO = new LeaveBalanceRequestDTO();
        updateDTO.setAvailable(18);
        updateDTO.setTotal(20);

        mockMvc.perform(put("/api/leave-balances/" + balance.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(balance.getId()))
                .andExpect(jsonPath("$.available").value(18))
                .andExpect(jsonPath("$.total").value(20));
    }

    @Test
    void updateLeaveBalance_NegativeAvailable_Returns400() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        balance = leaveBalanceRepository.save(balance);

        LeaveBalanceRequestDTO updateDTO = new LeaveBalanceRequestDTO();
        updateDTO.setAvailable(-1);

        mockMvc.perform(put("/api/leave-balances/" + balance.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateLeaveBalance_Regression_UsedPlusAvailableExceedsTotal_Returns400() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 20, 5, 15);
        balance = leaveBalanceRepository.save(balance);

        LeaveBalanceRequestDTO updateDTO = new LeaveBalanceRequestDTO();
        updateDTO.setAvailable(18); // 18 available + 5 used = 23 > 20 total

        mockMvc.perform(put("/api/leave-balances/" + balance.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateLeaveBalance_Regression_UsedPlusAvailableExceedsTotal_WithTotal_Returns400() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 20, 5, 15);
        balance = leaveBalanceRepository.save(balance);

        LeaveBalanceRequestDTO updateDTO = new LeaveBalanceRequestDTO();
        updateDTO.setAvailable(18);
        updateDTO.setTotal(20); // 18 available + 5 used = 23 > 20 total

        mockMvc.perform(put("/api/leave-balances/" + balance.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateLeaveBalance_TotalLessThanAvailable_Returns400() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 20, 0, 20);
        balance = leaveBalanceRepository.save(balance);

        LeaveBalanceRequestDTO updateDTO = new LeaveBalanceRequestDTO();
        updateDTO.setAvailable(15);
        updateDTO.setTotal(10);

        mockMvc.perform(put("/api/leave-balances/" + balance.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteLeaveBalance_Success() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        balance = leaveBalanceRepository.save(balance);

        mockMvc.perform(delete("/api/leave-balances/" + balance.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertFalse(leaveBalanceRepository.existsById(balance.getId()));
    }

    @Test
    void deleteLeaveBalance_BlockedWhenPendingLeaveRequestExists() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        balance = leaveBalanceRepository.save(balance);

        LeaveRequest pendingRequest = new LeaveRequest();
        pendingRequest.setEmployee(testEmployee);
        pendingRequest.setLeaveType(testLeaveType);
        pendingRequest.setStartDate(LocalDate.of(2026, 11, 2));
        pendingRequest.setEndDate(LocalDate.of(2026, 11, 6));
        pendingRequest.setReason("Trip");
        pendingRequest.setStatus(LeaveStatus.PENDING);
        leaveRequestRepository.save(pendingRequest);

        mockMvc.perform(delete("/api/leave-balances/" + balance.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete leave balance with pending or approved leave requests"));
    }

    @Test
    void deleteLeaveBalance_BlockedWhenApprovedLeaveRequestExists() throws Exception {
        LeaveBalance balance = new LeaveBalance(testEmployee, testLeaveType, 2026, 12, 0, 12);
        balance = leaveBalanceRepository.save(balance);

        LeaveRequest approvedRequest = new LeaveRequest();
        approvedRequest.setEmployee(testEmployee);
        approvedRequest.setLeaveType(testLeaveType);
        approvedRequest.setStartDate(LocalDate.of(2026, 11, 2));
        approvedRequest.setEndDate(LocalDate.of(2026, 11, 6));
        approvedRequest.setReason("Trip");
        approvedRequest.setStatus(LeaveStatus.APPROVED);
        leaveRequestRepository.save(approvedRequest);

        mockMvc.perform(delete("/api/leave-balances/" + balance.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteLeaveBalance_NotFound() throws Exception {
        mockMvc.perform(delete("/api/leave-balances/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void security_EmployeeCannotManageLeaveBalances() throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(testEmployee.getId());
        dto.setLeaveTypeId(testLeaveType.getId());
        dto.setAvailable(10);

        // POST /api/leave-balances -> 403 Forbidden
        mockMvc.perform(post("/api/leave-balances")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // POST /employee/{id}/initialize -> 403 Forbidden
        mockMvc.perform(post("/api/leave-balances/employee/" + testEmployee.getId() + "/initialize")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void security_EmployeeCannotAccessOtherEmployeeBalances() throws Exception {
        mockMvc.perform(get("/api/leave-balances/employee/" + otherEmployee.getId())
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }
}
