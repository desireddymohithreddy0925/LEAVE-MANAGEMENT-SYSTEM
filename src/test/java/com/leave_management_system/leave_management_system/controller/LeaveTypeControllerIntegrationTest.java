package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveBalance;
import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveBalanceRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
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
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LeaveTypeControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

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
    private Employee testEmployee;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Setup Admin User & Token
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        User adminUser = new User();
        adminUser.setEmail("admin-" + suffix + "@test.com");
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

        // 2. Setup Employee User & Token
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        User empUser = new User();
        empUser.setEmail("emp-" + suffix + "@test.com");
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

        // 3. Create Department and Employee for relational tests
        Department department = new Department();
        department.setName("Dept-" + suffix);
        departmentRepository.save(department);

        testEmployee = new Employee();
        testEmployee.setFirstName("LeaveType");
        testEmployee.setLastName("Tester");
        testEmployee.setEmployeeCode("EMP-LT-" + suffix);
        testEmployee.setSalary(new BigDecimal("60000"));
        testEmployee.setStatus("ACTIVE");
        testEmployee.setDepartment(department);
        testEmployee.setUser(empUser);
        employeeRepository.save(testEmployee);
    }

    @Test
    void createLeaveType_Success() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO("Maternity Leave", "Paid maternity leave", 90);

        mockMvc.perform(post("/api/leave-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Maternity Leave"))
                .andExpect(jsonPath("$.description").value("Paid maternity leave"))
                .andExpect(jsonPath("$.defaultDays").value(90))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createLeaveType_ValidationError() throws Exception {
        LeaveTypeRequestDTO invalidDto = new LeaveTypeRequestDTO();
        // name is blank, defaultDays is null

        mockMvc.perform(post("/api/leave-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.defaultDays").exists());
    }

    @Test
    void createLeaveType_DuplicateName() throws Exception {
        LeaveType existing = new LeaveType("Casual Leave", "Casual", 10);
        leaveTypeRepository.save(existing);

        LeaveTypeRequestDTO duplicateDto = new LeaveTypeRequestDTO("Casual Leave", "Another casual", 12);

        mockMvc.perform(post("/api/leave-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Leave type already exists"));
    }

    @Test
    void getAllLeaveTypes_Authenticated_And_FilterActive() throws Exception {
        LeaveType activeType = new LeaveType("Active Vacation", "Vacation", 15);
        activeType.setActive(true);
        leaveTypeRepository.save(activeType);

        LeaveType inactiveType = new LeaveType("Archived Type", "Archived", 5);
        inactiveType.setActive(false);
        leaveTypeRepository.save(inactiveType);

        // 1. Get all leave types (both active and inactive)
        mockMvc.perform(get("/api/leave-types")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 2. Filter active = true
        mockMvc.perform(get("/api/leave-types")
                        .param("active", "true")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Active Vacation')].active").value(true));

        // 3. Filter active = false
        mockMvc.perform(get("/api/leave-types")
                        .param("active", "false")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Archived Type')].active").value(false));
    }

    @Test
    void getLeaveTypeById_Success() throws Exception {
        LeaveType type = new LeaveType("Study Leave", "Education", 30);
        leaveTypeRepository.save(type);

        mockMvc.perform(get("/api/leave-types/" + type.getId())
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(type.getId()))
                .andExpect(jsonPath("$.name").value("Study Leave"));
    }

    @Test
    void getLeaveTypeById_NotFound() throws Exception {
        mockMvc.perform(get("/api/leave-types/99999")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Leave type not found with id: 99999"));
    }

    @Test
    void updateLeaveType_Success() throws Exception {
        LeaveType type = new LeaveType("Paternity Leave", "Paternity", 10);
        leaveTypeRepository.save(type);

        LeaveTypeRequestDTO updateDto = new LeaveTypeRequestDTO("Paternity Leave", "Updated paternity description", 15);

        mockMvc.perform(put("/api/leave-types/" + type.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated paternity description"))
                .andExpect(jsonPath("$.defaultDays").value(15));
    }

    @Test
    void updateLeaveType_NotFound() throws Exception {
        LeaveTypeRequestDTO updateDto = new LeaveTypeRequestDTO("Non-existent", "Desc", 10);

        mockMvc.perform(put("/api/leave-types/99999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateLeaveType_DuplicateName() throws Exception {
        LeaveType type1 = new LeaveType("Unique Alpha", "Alpha", 10);
        leaveTypeRepository.save(type1);

        LeaveType type2 = new LeaveType("Unique Beta", "Beta", 12);
        leaveTypeRepository.save(type2);

        LeaveTypeRequestDTO conflictDto = new LeaveTypeRequestDTO("Unique Alpha", "Beta updated to alpha name", 15);

        mockMvc.perform(put("/api/leave-types/" + type2.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Leave type already exists"));
    }

    @Test
    void activateAndDeactivateLeaveType_Success() throws Exception {
        LeaveType type = new LeaveType("Toggle Leave", "Desc", 10);
        leaveTypeRepository.save(type);

        // Deactivate
        mockMvc.perform(put("/api/leave-types/" + type.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Activate
        mockMvc.perform(put("/api/leave-types/" + type.getId() + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deleteLeaveType_Success() throws Exception {
        LeaveType type = new LeaveType("Temporary Leave", "Temporary", 5);
        leaveTypeRepository.save(type);

        mockMvc.perform(delete("/api/leave-types/" + type.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertFalse(leaveTypeRepository.findById(type.getId()).isPresent());
    }

    @Test
    void deleteLeaveType_NotFound() throws Exception {
        mockMvc.perform(delete("/api/leave-types/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteLeaveType_WithExistingBalance_Fails() throws Exception {
        LeaveType type = new LeaveType("Balance Protected", "Desc", 10);
        leaveTypeRepository.save(type);

        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(testEmployee);
        balance.setLeaveType(type);
        balance.setYear(2026);
        balance.setTotal(10);
        balance.setUsed(0);
        balance.setAvailable(10);
        leaveBalanceRepository.save(balance);

        mockMvc.perform(delete("/api/leave-types/" + type.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete leave type with existing balances or requests"));
    }

    @Test
    void deleteLeaveType_WithExistingRequest_Fails() throws Exception {
        LeaveType type = new LeaveType("Request Protected", "Desc", 10);
        leaveTypeRepository.save(type);

        LeaveRequest request = new LeaveRequest();
        request.setEmployee(testEmployee);
        request.setLeaveType(type);
        request.setStartDate(LocalDate.now());
        request.setEndDate(LocalDate.now().plusDays(2));
        request.setStatus(LeaveStatus.PENDING);
        leaveRequestRepository.save(request);

        mockMvc.perform(delete("/api/leave-types/" + type.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot delete leave type with existing balances or requests"));
    }

    @Test
    void security_EmployeeCannotMutateLeaveTypes() throws Exception {
        LeaveType type = new LeaveType("Protected Leave", "Desc", 10);
        leaveTypeRepository.save(type);

        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO("New Type", "Desc", 10);

        // 1. Employee cannot create leave type -> 403 Forbidden
        mockMvc.perform(post("/api/leave-types")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // 2. Employee cannot update leave type -> 403 Forbidden
        mockMvc.perform(put("/api/leave-types/" + type.getId())
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // 3. Employee cannot delete leave type -> 403 Forbidden
        mockMvc.perform(delete("/api/leave-types/" + type.getId())
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        // 4. Employee cannot activate / deactivate leave type -> 403 Forbidden
        mockMvc.perform(put("/api/leave-types/" + type.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/leave-types/" + type.getId() + "/activate")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void security_UnauthenticatedCannotAccessLeaveTypes() throws Exception {
        // Unauthenticated calls must return 401 Unauthorized
        mockMvc.perform(get("/api/leave-types"))
                .andExpect(status().isUnauthorized());

        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO("Unauth Type", "Desc", 10);
        mockMvc.perform(post("/api/leave-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }
}
