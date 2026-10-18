package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.entity.AuditLog;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.AuditLogRepository;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.service.AuditService;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AuditLogControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private String employeeToken;
    private User adminUser;
    private User employeeUser;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Setup Admin User & Token
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        adminUser = new User();
        adminUser.setEmail("admin-" + suffix + "@test.com");
        adminUser.setPassword("password");
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

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
        employeeUser = new User();
        employeeUser.setEmail("emp-" + suffix + "@test.com");
        employeeUser.setPassword("password");
        employeeUser.setActive(true);
        employeeUser.getRoles().add(empRole);
        employeeUser = userRepository.save(employeeUser);

        UserDetails empDetails = new org.springframework.security.core.userdetails.User(
                employeeUser.getEmail(),
                employeeUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );
        employeeToken = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(empDetails, null, empDetails.getAuthorities())
        );
    }

    private AuditLog createTestAuditLog(String action, String entityType, Long entityId, User user) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setUser(user);
        log.setTimestamp(LocalDateTime.now());
        log.setIpAddress("127.0.0.1");
        log.setOldValue(null);
        log.setNewValue("{\"test\":\"data\"}");
        return auditLogRepository.save(log);
    }

    @Test
    void searchAuditLogs_AsAdmin_Returns200AndPage() throws Exception {
        createTestAuditLog("LEAVE_APPLIED", "LeaveRequest", 101L, employeeUser);

        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
    }

    @Test
    void searchAuditLogs_FilterByAction_ReturnsMatchingLogs() throws Exception {
        createTestAuditLog("LEAVE_APPROVED", "LeaveRequest", 102L, adminUser);
        createTestAuditLog("LEAVE_REJECTED", "LeaveRequest", 103L, adminUser);

        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "LEAVE_APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].action", everyItem(is("LEAVE_APPROVED"))));
    }

    @Test
    void searchAuditLogs_FilterByEntityType_ReturnsMatchingLogs() throws Exception {
        createTestAuditLog("EMPLOYEE_CREATED", "Employee", 201L, adminUser);
        createTestAuditLog("DEPARTMENT_CREATED", "Department", 301L, adminUser);

        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("entityType", "Department"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].entityType", everyItem(is("Department"))));
    }

    @Test
    void searchAuditLogs_FilterByEntityId_ReturnsMatchingLogs() throws Exception {
        AuditLog target = createTestAuditLog("LEAVE_CANCELLED", "LeaveRequest", 777L, employeeUser);

        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("entityId", "777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].entityId", is(777)));
    }

    @Test
    void searchAuditLogs_FilterByDateRange_ReturnsMatchingLogs() throws Exception {
        AuditLog log = createTestAuditLog("BALANCE_UPDATED", "LeaveBalance", 501L, adminUser);

        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);

        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("startDate", start.toString())
                        .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
    }

    @Test
    void searchAuditLogs_AsEmployee_Returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchAuditLogs_Unauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAuditLogById_AsAdmin_Returns200() throws Exception {
        AuditLog log = createTestAuditLog("LEAVE_APPLIED", "LeaveRequest", 111L, employeeUser);

        mockMvc.perform(get("/api/audit-logs/" + log.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(log.getId().intValue())))
                .andExpect(jsonPath("$.action", is("LEAVE_APPLIED")))
                .andExpect(jsonPath("$.entityType", is("LeaveRequest")))
                .andExpect(jsonPath("$.entityId", is(111)))
                .andExpect(jsonPath("$.userEmail", is(employeeUser.getEmail())));
    }

    @Test
    void getAuditLogById_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/audit-logs/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Audit log not found with id")));
    }

    @Test
    void getAuditLogById_AsEmployee_Returns403Forbidden() throws Exception {
        AuditLog log = createTestAuditLog("LEAVE_APPLIED", "LeaveRequest", 112L, employeeUser);

        mockMvc.perform(get("/api/audit-logs/" + log.getId())
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAuditLogsByEntity_AsAdmin_Returns200() throws Exception {
        createTestAuditLog("LEAVE_APPLIED", "LeaveRequest", 888L, employeeUser);
        createTestAuditLog("LEAVE_APPROVED", "LeaveRequest", 888L, adminUser);

        mockMvc.perform(get("/api/audit-logs/entity/LeaveRequest/888")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].entityId", everyItem(is(888))));
    }

    @Test
    void getAuditLogsByEntity_AsEmployee_Returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/audit-logs/entity/LeaveRequest/888")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLog_GeneratedOnDepartmentLifecycle() throws Exception {
        String deptName = "AuditTestDept-" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Create Department
        DepartmentRequestDTO createDto = new DepartmentRequestDTO();
        createDto.setName(deptName);

        String createRes = mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long deptId = objectMapper.readTree(createRes).get("id").asLong();

        // Verify DEPARTMENT_CREATED audit log exists
        boolean hasCreatedAudit = auditLogRepository.findAll().stream()
                .anyMatch(l -> "DEPARTMENT_CREATED".equals(l.getAction()) && deptId.equals(l.getEntityId()));
        assertTrue(hasCreatedAudit, "Expected DEPARTMENT_CREATED audit log to be persisted");

        // 2. Update Department
        DepartmentRequestDTO updateDto = new DepartmentRequestDTO();
        updateDto.setName(deptName + "-Updated");

        mockMvc.perform(put("/api/departments/" + deptId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        boolean hasUpdatedAudit = auditLogRepository.findAll().stream()
                .anyMatch(l -> "DEPARTMENT_UPDATED".equals(l.getAction()) && deptId.equals(l.getEntityId()));
        assertTrue(hasUpdatedAudit, "Expected DEPARTMENT_UPDATED audit log to be persisted");

        // 3. Delete Department
        mockMvc.perform(delete("/api/departments/" + deptId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        boolean hasDeletedAudit = auditLogRepository.findAll().stream()
                .anyMatch(l -> "DEPARTMENT_DELETED".equals(l.getAction()) && deptId.equals(l.getEntityId()));
        assertTrue(hasDeletedAudit, "Expected DEPARTMENT_DELETED audit log to be persisted");
    }

    @Test
    void auditLog_GeneratedOnUnauthorizedAccess() throws Exception {
        // Employee attempts to access admin audit logs
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        // Verify UNAUTHORIZED_ACCESS security event is logged
        boolean hasUnauthorizedLog = auditLogRepository.findAll().stream()
                .anyMatch(l -> "UNAUTHORIZED_ACCESS".equals(l.getAction()) && "SECURITY_EVENT".equals(l.getEntityType()));
        assertTrue(hasUnauthorizedLog, "Expected UNAUTHORIZED_ACCESS security event to be recorded");
    }

    @Test
    void auditLog_SensitiveDataMaskedInLog() {
        String sensitiveJson = "{\"email\":\"user@test.com\",\"password\":\"SuperSecretPassword!\",\"token\":\"xyz\"}";
        auditService.logAction("USER_UPDATE", "User", 999L, null, sensitiveJson);

        AuditLog saved = auditLogRepository.findAll().stream()
                .filter(l -> Long.valueOf(999L).equals(l.getEntityId()) && "USER_UPDATE".equals(l.getAction()))
                .findFirst()
                .orElseThrow();

        assertFalse(saved.getNewValue().contains("SuperSecretPassword!"));
        assertFalse(saved.getNewValue().contains("xyz"));
        assertTrue(saved.getNewValue().contains("[REDACTED]"));
    }
}
