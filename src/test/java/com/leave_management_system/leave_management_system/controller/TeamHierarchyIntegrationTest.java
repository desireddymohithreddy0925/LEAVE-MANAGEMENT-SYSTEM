package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import com.leave_management_system.leave_management_system.dto.RegisterRequestDTO;
import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TeamHierarchyIntegrationTest {

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

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String adminToken;
    private String manager1Token;
    private String manager2Token;
    private String empToken;

    private Long manager1EmpId;
    private Long manager2EmpId;
    private Long leadEmpId;
    private Long engineerEmpId;
    private Long dept1Id;
    private Long dept2Id;

    @BeforeEach
    public void setup() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // 1. Create Admin
        adminToken = registerAndElevate("admin.team@test.com", "ADMIN");

        // 2. Create Departments
        dept1Id = createDepartment("Engineering", adminToken);
        dept2Id = createDepartment("Marketing", adminToken);

        // 3. Create Managers
        manager1EmpId = createEmployee("m1.team@test.com", "MANAGER", dept1Id);
        manager1Token = getAuthToken("m1.team@test.com", "password123");

        manager2EmpId = createEmployee("m2.team@test.com", "MANAGER", dept2Id);
        manager2Token = getAuthToken("m2.team@test.com", "password123");

        // Assign manager1 to dept1
        mockMvc.perform(patch("/api/departments/" + dept1Id + "/manager")
                .header("Authorization", "Bearer " + adminToken)
                .param("managerId", String.valueOf(manager1EmpId)))
                .andExpect(status().isOk());

        // Assign manager2 to dept2
        mockMvc.perform(patch("/api/departments/" + dept2Id + "/manager")
                .header("Authorization", "Bearer " + adminToken)
                .param("managerId", String.valueOf(manager2EmpId)))
                .andExpect(status().isOk());

        // 4. Create Team Lead reporting to Manager 1
        leadEmpId = createEmployee("lead.team@test.com", "EMPLOYEE", dept1Id);
        mockMvc.perform(patch("/api/employees/" + leadEmpId + "/manager")
                .header("Authorization", "Bearer " + adminToken)
                .param("managerId", String.valueOf(manager1EmpId)))
                .andExpect(status().isOk());

        // 5. Create Engineer reporting to Team Lead
        engineerEmpId = createEmployee("eng.team@test.com", "EMPLOYEE", dept1Id);
        mockMvc.perform(patch("/api/employees/" + engineerEmpId + "/manager")
                .header("Authorization", "Bearer " + adminToken)
                .param("managerId", String.valueOf(leadEmpId)))
                .andExpect(status().isOk());

        empToken = getAuthToken("eng.team@test.com", "password123");
    }

    private String getAuthToken(String email, String password) throws Exception {
        String authPayload = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(authPayload))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.accessToken", String.class);
    }

    private String registerAndElevate(String email, String roleName) throws Exception {
        RegisterRequestDTO register = new RegisterRequestDTO();
        register.setFirstName("First");
        register.setLastName("Last");
        register.setEmail(email);
        register.setPassword("password123");
        register.setEmployeeCode("CODE-" + System.currentTimeMillis() + "-" + (int)(Math.random()*1000));
        register.setPhone("1234567890");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        Role role = roleRepository.findByName(roleName).orElseThrow();
        user.getRoles().clear();
        user.getRoles().add(role);
        userRepository.save(user);

        return getAuthToken(email, "password123");
    }

    private Long createDepartment(String name, String token) throws Exception {
        com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO dto =
                new com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO();
        dto.setName(name + " " + System.currentTimeMillis() + " " + (int)(Math.random()*1000));

        MvcResult result = mockMvc.perform(post("/api/departments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Long createEmployee(String email, String roleName, Long deptId) throws Exception {
        registerAndElevate(email, roleName);
        Employee emp = employeeRepository.findByUserEmail(email).orElseThrow();
        Department dept = departmentRepository.findById(deptId).orElseThrow();
        emp.setDepartment(dept);
        return employeeRepository.save(emp).getId();
    }

    @Test
    void testManagerCanViewOwnTeam() throws Exception {
        // Manager 1 requests /api/managers/me/team
        mockMvc.perform(get("/api/managers/me/team")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].id", hasItem(leadEmpId.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(manager2EmpId.intValue()))));
    }

    @Test
    void testManagerCannotViewAnotherManagersTeam_Returns403() throws Exception {
        // Manager 1 tries to view Manager 2's team -> 403 Forbidden
        mockMvc.perform(get("/api/managers/" + manager2EmpId + "/team")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // Manager 1 views own team via ID -> 200 OK
        mockMvc.perform(get("/api/managers/" + manager1EmpId + "/team")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk());

        // Normal Employee tries to view Manager 1's team -> 403 Forbidden
        mockMvc.perform(get("/api/managers/" + manager1EmpId + "/team")
                .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isForbidden());

        // Admin can view any manager's team -> 200 OK
        mockMvc.perform(get("/api/managers/" + manager2EmpId + "/team")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void testTeamHierarchyTree() throws Exception {
        // Manager 1 retrieves team hierarchy
        mockMvc.perform(get("/api/managers/me/team-hierarchy")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(manager1EmpId.intValue()))
                .andExpect(jsonPath("$.directReports", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.subordinateTeams", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.totalTeamSize", greaterThanOrEqualTo(2)));

        // Manager 2 tries to view Manager 1's team hierarchy -> 403 Forbidden
        mockMvc.perform(get("/api/managers/" + manager1EmpId + "/team-hierarchy")
                .header("Authorization", "Bearer " + manager2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPreventInvalidManagerAssignments() throws Exception {
        // 1. Self-assignment as manager -> 400 Bad Request
        mockMvc.perform(patch("/api/employees/" + leadEmpId + "/manager")
                .header("Authorization", "Bearer " + adminToken)
                .param("managerId", String.valueOf(leadEmpId)))
                .andExpect(status().isBadRequest());

        // 2. Assigning inactive employee as manager -> 409 Conflict
        Employee inactiveEmp = employeeRepository.findById(manager2EmpId).orElseThrow();
        inactiveEmp.setStatus("INACTIVE");
        employeeRepository.save(inactiveEmp);

        mockMvc.perform(patch("/api/employees/" + leadEmpId + "/manager")
                .header("Authorization", "Bearer " + adminToken)
                .param("managerId", String.valueOf(manager2EmpId)))
                .andExpect(status().isConflict());

        // 3. Circular assignment: Manager 1 reports to Lead, while Lead already reports to Manager 1! -> 400 Bad Request
        mockMvc.perform(patch("/api/employees/" + manager1EmpId + "/manager")
                .header("Authorization", "Bearer " + adminToken)
                .param("managerId", String.valueOf(leadEmpId)))
                .andExpect(status().isBadRequest());

        // 4. Multi-level circular assignment: Manager 1 reports to Engineer (Engineer -> Lead -> Manager 1) -> 400 Bad Request
        mockMvc.perform(patch("/api/employees/" + manager1EmpId + "/manager")
                .header("Authorization", "Bearer " + adminToken)
                .param("managerId", String.valueOf(engineerEmpId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDepartmentHierarchyEndpoints() throws Exception {
        // Admin or Manager with DEPARTMENT_VIEW gets department hierarchy
        mockMvc.perform(get("/api/departments/" + dept1Id + "/hierarchy")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(dept1Id.intValue()))
                .andExpect(jsonPath("$.manager.id").value(manager1EmpId.intValue()))
                .andExpect(jsonPath("$.employees", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.totalEmployees", greaterThanOrEqualTo(2)));

        // Get all department hierarchies
        mockMvc.perform(get("/api/departments/hierarchy")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void testEmployeeHierarchyEndpoint() throws Exception {
        // Engineer views own reporting chain upwards and subordinates downwards
        mockMvc.perform(get("/api/employees/" + engineerEmpId + "/hierarchy")
                .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.id").value(engineerEmpId.intValue()))
                .andExpect(jsonPath("$.reportingChain", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.reportingChain[0].id").value(leadEmpId.intValue()))
                .andExpect(jsonPath("$.reportingChain[1].id").value(manager1EmpId.intValue()));

        // Manager 1 can view engineer's hierarchy (manager of employee)
        mockMvc.perform(get("/api/employees/" + engineerEmpId + "/hierarchy")
                .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk());

        // Manager 2 cannot view engineer's hierarchy (different team) -> 403 Forbidden
        mockMvc.perform(get("/api/employees/" + engineerEmpId + "/hierarchy")
                .header("Authorization", "Bearer " + manager2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void testEmployeeTransferUpdatesHierarchy() throws Exception {
        // Transfer Lead from Dept 1 to Dept 2
        mockMvc.perform(put("/api/employees/" + leadEmpId + "/department")
                .header("Authorization", "Bearer " + adminToken)
                .param("departmentId", String.valueOf(dept2Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.id").value(dept2Id.intValue()));

        // Verify Lead is now in Dept 2's employees list
        mockMvc.perform(get("/api/departments/" + dept2Id + "/employees")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(leadEmpId.intValue())));
    }
}
