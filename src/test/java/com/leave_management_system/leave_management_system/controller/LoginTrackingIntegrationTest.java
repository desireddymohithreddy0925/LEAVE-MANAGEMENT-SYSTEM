package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.AuthRequestDTO;
import com.leave_management_system.leave_management_system.dto.AuthResponseDTO;
import com.leave_management_system.leave_management_system.entity.LoginHistory;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LoginHistoryRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class LoginTrackingIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.AuditLogRepository auditLogRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private User adminUser;
    private String adminToken;
    private User employeeUser;
    private String employeeToken;
    private String uniqueSuffix;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();

        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Setup Admin
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        adminUser = new User();
        adminUser.setEmail("track-admin-" + uniqueSuffix + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
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

        // Setup Employee
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        employeeUser = new User();
        employeeUser.setEmail("track-emp-" + uniqueSuffix + "@test.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
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

    @AfterEach
    public void cleanup() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().contains(uniqueSuffix))
                .forEach(u -> {
                    employeeRepository.findByUserEmail(u.getEmail()).ifPresent(employeeRepository::delete);
                    userRepository.delete(u);
                });
    }

    // 1. Successful login updates user tracking fields and records login history
    @Test
    void testSuccessfulLogin_UpdatesUserTrackingAndRecordsHistory() throws Exception {
        AuthRequestDTO loginRequest = new AuthRequestDTO();
        loginRequest.setEmail(employeeUser.getEmail());
        loginRequest.setPassword("EmpPass123!");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "Mozilla/5.0 TestBrowser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponseDTO response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponseDTO.class);
        assertNotNull(response.getUser());
        assertEquals(1L, response.getUser().getLoginCount());
        assertNotNull(response.getUser().getLastLoginAt());

        // Verify database user record
        User updatedUser = userRepository.findById(employeeUser.getId()).orElseThrow();
        assertEquals(1L, updatedUser.getLoginCount());
        assertNotNull(updatedUser.getLastLoginAt());
        assertNotNull(updatedUser.getLastLoginIp());

        // Verify login_history entry
        LoginHistory history = loginHistoryRepository.findAll().stream()
                .filter(h -> "SUCCESS".equals(h.getStatus()) && employeeUser.getEmail().equals(h.getEmail()))
                .findFirst()
                .orElse(null);

        assertNotNull(history, "Expected SUCCESS login_history record");
        assertEquals("Mozilla/5.0 TestBrowser", history.getUserAgent());
        assertNotNull(history.getLoginTime());
        assertNull(history.getFailureReason());
    }

    // 2. Failed login records failed history and does not leak password
    @Test
    void testFailedLogin_RecordsFailedHistory_AndDoesNotLeakPassword() throws Exception {
        String wrongPassword = "SecretWrongPassword123!";
        AuthRequestDTO loginRequest = new AuthRequestDTO();
        loginRequest.setEmail(employeeUser.getEmail());
        loginRequest.setPassword(wrongPassword);

        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "AttackerBrowser/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());

        // Verify login_history entry
        LoginHistory failedHistory = loginHistoryRepository.findAll().stream()
                .filter(h -> "FAILED".equals(h.getStatus()) && employeeUser.getEmail().equals(h.getEmail()))
                .findFirst()
                .orElse(null);

        assertNotNull(failedHistory, "Expected FAILED login_history record");
        assertEquals("AttackerBrowser/1.0", failedHistory.getUserAgent());
        assertNotNull(failedHistory.getFailureReason());
        assertFalse(failedHistory.getFailureReason().contains(wrongPassword),
                "Password must NEVER be leaked in login history failure reason");

        // Verify user login_count did not increment
        User unchangedUser = userRepository.findById(employeeUser.getId()).orElseThrow();
        assertEquals(0L, unchangedUser.getLoginCount());
    }

    // 3. Consecutive logins increment count and update lastLoginAt
    @Test
    void testMultipleLogins_IncrementsLoginCountAndUpdatesTimestamp() throws Exception {
        AuthRequestDTO loginRequest = new AuthRequestDTO();
        loginRequest.setEmail(employeeUser.getEmail());
        loginRequest.setPassword("EmpPass123!");

        // First login
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        // Second login
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findById(employeeUser.getId()).orElseThrow();
        assertEquals(2L, updatedUser.getLoginCount());

        long successCount = loginHistoryRepository.countByUserIdAndStatus(employeeUser.getId(), "SUCCESS");
        assertEquals(2, successCount);
    }

    // 4. Employee views their own login history via /api/login-history/me
    @Test
    void testGetMyLoginHistory_AsEmployee_ReturnsOwnHistory() throws Exception {
        // Generate a login event for employee
        AuthRequestDTO loginRequest = new AuthRequestDTO();
        loginRequest.setEmail(employeeUser.getEmail());
        loginRequest.setPassword("EmpPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        // Query employee's own login history
        mockMvc.perform(get("/api/login-history/me")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[0].userEmail", is(employeeUser.getEmail())))
                .andExpect(jsonPath("$.content[0].status", is("SUCCESS")));

        // Convenience endpoint on /api/auth/login-history
        mockMvc.perform(get("/api/auth/login-history")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[0].userEmail", is(employeeUser.getEmail())));
    }

    // 5. Employee views their own login summary via /api/login-history/me/summary
    @Test
    void testGetMyLoginSummary_AsEmployee_ReturnsSummary() throws Exception {
        AuthRequestDTO loginRequest = new AuthRequestDTO();
        loginRequest.setEmail(employeeUser.getEmail());
        loginRequest.setPassword("EmpPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/login-history/me/summary")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(employeeUser.getEmail())))
                .andExpect(jsonPath("$.totalLogins", is(1)))
                .andExpect(jsonPath("$.lastLoginAt", notNullValue()));
    }

    // 6. Admin searches all login history with filters
    @Test
    void testSearchLoginHistory_AsAdmin_ReturnsFilteredResults() throws Exception {
        // Produce a success and a failure
        AuthRequestDTO successReq = new AuthRequestDTO();
        successReq.setEmail(adminUser.getEmail());
        successReq.setPassword("AdminPass123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(successReq)))
                .andExpect(status().isOk());

        AuthRequestDTO failReq = new AuthRequestDTO();
        failReq.setEmail(employeeUser.getEmail());
        failReq.setPassword("WrongPass!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failReq)))
                .andExpect(status().isUnauthorized());

        // Admin searches all with status=FAILED
        mockMvc.perform(get("/api/login-history")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("FAILED"))));

        // Admin searches all with status=SUCCESS
        mockMvc.perform(get("/api/login-history")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("SUCCESS"))));
    }

    // 7. Admin gets specific user login history
    @Test
    void testGetUserLoginHistory_AsAdmin_Returns200() throws Exception {
        mockMvc.perform(get("/api/login-history/user/" + employeeUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()));
    }

    // 8. Admin gets specific user login summary
    @Test
    void testGetUserLoginSummary_AsAdmin_Returns200() throws Exception {
        mockMvc.perform(get("/api/login-history/user/" + employeeUser.getId() + "/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(employeeUser.getEmail())))
                .andExpect(jsonPath("$.totalLogins", notNullValue()));
    }

    // 9. Employee accessing admin search endpoint receives 403 Forbidden
    @Test
    void testSearchLoginHistory_AsEmployee_Returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/login-history")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    // 10. Unauthenticated access receives 401 Unauthorized
    @Test
    void testGetMyLoginHistory_Unauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/login-history/me"))
                .andExpect(status().isUnauthorized());
    }
}
