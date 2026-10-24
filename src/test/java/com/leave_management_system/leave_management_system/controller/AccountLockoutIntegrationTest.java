package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.AuthRequestDTO;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.SecurityEvent;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.security.SecurityEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class AccountLockoutIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private User targetUser;
    private User adminUser;
    private String adminToken;
    private User regularUser;
    private String regularUserToken;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        cleanup();

        // 1. Setup Admin Role and User
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role r = new Role("ADMIN");
            return roleRepository.save(r);
        });

        Permission lockManagePerm = permissionRepository.findByName("USER_LOCK_MANAGE").orElseGet(() -> {
            Permission p = new Permission("USER_LOCK_MANAGE", "Manually lock and unlock user accounts");
            return permissionRepository.save(p);
        });
        adminRole.getPermissions().add(lockManagePerm);
        roleRepository.save(adminRole);

        adminUser = new User();
        adminUser.setEmail("admin-lockout@test.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.setRoles(Set.of(adminRole));
        adminUser = userRepository.save(adminUser);

        adminToken = jwtUtils.generateTokenFromUsername(adminUser.getEmail());

        // 2. Setup Regular Employee Role and User
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> {
            Role r = new Role("EMPLOYEE");
            return roleRepository.save(r);
        });

        regularUser = new User();
        regularUser.setEmail("employee-lockout@test.com");
        regularUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        regularUser.setActive(true);
        regularUser.setAccountNonLocked(true);
        regularUser.setRoles(Set.of(employeeRole));
        regularUser = userRepository.save(regularUser);

        regularUserToken = jwtUtils.generateTokenFromUsername(regularUser.getEmail());

        // 3. Setup Target User for Lockout Tests
        targetUser = new User();
        targetUser.setEmail("target-lockout@test.com");
        targetUser.setPassword(passwordEncoder.encode("TargetPass123!"));
        targetUser.setActive(true);
        targetUser.setAccountNonLocked(true);
        targetUser.setFailedLoginAttempts(0);
        targetUser.setRoles(Set.of(employeeRole));
        targetUser = userRepository.save(targetUser);
    }

    @Test
    public void consecutiveFailedLogins_triggersAccountLockout_andRecordsSecurityEvent() throws Exception {
        AuthRequestDTO badCredentialsRequest = new AuthRequestDTO();
        badCredentialsRequest.setEmail(targetUser.getEmail());
        badCredentialsRequest.setPassword("WrongPassword!");

        // Send 4 failed login attempts (threshold is 5)
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badCredentialsRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));

            User refreshed = userRepository.findById(targetUser.getId()).orElseThrow();
            assertEquals(i, refreshed.getFailedLoginAttempts(), "Failed attempts counter should equal " + i);
            assertTrue(refreshed.isAccountNonLocked(), "Account should still be unlocked before 5th failure");
        }

        // 5th failed attempt -> Must trigger account lockout
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCredentialsRequest)))
                .andExpect(status().isUnauthorized());

        User lockedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertEquals(5, lockedUser.getFailedLoginAttempts());
        assertFalse(lockedUser.isAccountNonLocked(), "Account must be locked after 5 failed attempts");
        assertNotNull(lockedUser.getLockExpiresAt(), "Lock expiration timestamp must be set");
        assertTrue(lockedUser.getLockExpiresAt().isAfter(LocalDateTime.now()), "Lock expiration should be in the future");

        // Verify ACCOUNT_LOCKED security event was emitted
        List<SecurityEvent> events = securityEventRepository.findByEventType(SecurityEventType.ACCOUNT_LOCKED);
        assertFalse(events.isEmpty(), "ACCOUNT_LOCKED security event should be generated");
        SecurityEvent lockEvent = events.get(0);
        assertEquals(targetUser.getEmail(), lockEvent.getEmail());
        assertTrue(lockEvent.getDetails().contains("Account locked after 5 consecutive failed login attempts"));
    }

    @Test
    public void loginWithLockedAccount_returnsUnauthorizedWithAccountLockedMessage() throws Exception {
        // Manually lock user
        targetUser.setAccountNonLocked(false);
        targetUser.setFailedLoginAttempts(5);
        targetUser.setLockExpiresAt(LocalDateTime.now().plusMinutes(15));
        userRepository.save(targetUser);

        // Attempt login with CORRECT password
        AuthRequestDTO correctCredentials = new AuthRequestDTO();
        correctCredentials.setEmail(targetUser.getEmail());
        correctCredentials.setPassword("TargetPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctCredentials)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Account Locked"))
                .andExpect(jsonPath("$.message").value("User account is locked due to multiple failed login attempts. Please try again later or contact an administrator."));
    }

    @Test
    public void successfulLogin_resetsFailedAttemptsCounter() throws Exception {
        // Simulate 2 failed attempts
        AuthRequestDTO badCredentials = new AuthRequestDTO();
        badCredentials.setEmail(targetUser.getEmail());
        badCredentials.setPassword("WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCredentials)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCredentials)))
                .andExpect(status().isUnauthorized());

        User userAfterFails = userRepository.findById(targetUser.getId()).orElseThrow();
        assertEquals(2, userAfterFails.getFailedLoginAttempts());

        // Now login with CORRECT credentials
        AuthRequestDTO correctCredentials = new AuthRequestDTO();
        correctCredentials.setEmail(targetUser.getEmail());
        correctCredentials.setPassword("TargetPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctCredentials)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        User userAfterSuccess = userRepository.findById(targetUser.getId()).orElseThrow();
        assertEquals(0, userAfterSuccess.getFailedLoginAttempts(), "Failed attempts counter should be reset to 0");
        assertNull(userAfterSuccess.getLockExpiresAt(), "Lock expiration should be cleared");
        assertTrue(userAfterSuccess.isAccountNonLocked());
    }

    @Test
    public void adminCanUnlockAccount_allowingUserToLoginAgain() throws Exception {
        // Lock user
        targetUser.setAccountNonLocked(false);
        targetUser.setFailedLoginAttempts(5);
        targetUser.setLockExpiresAt(LocalDateTime.now().plusMinutes(15));
        userRepository.save(targetUser);

        // Admin unlocks user account via PUT /api/users/{id}/unlock
        mockMvc.perform(put("/api/users/" + targetUser.getId() + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNonLocked").value(true))
                .andExpect(jsonPath("$.failedLoginAttempts").value(0))
                .andExpect(jsonPath("$.lockExpiresAt").doesNotExist());

        User unlockedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertTrue(unlockedUser.isAccountNonLocked());
        assertEquals(0, unlockedUser.getFailedLoginAttempts());
        assertNull(unlockedUser.getLockExpiresAt());

        // Verify security event ACCOUNT_UNLOCKED recorded
        List<SecurityEvent> events = securityEventRepository.findByEventType(SecurityEventType.ACCOUNT_UNLOCKED);
        assertFalse(events.isEmpty(), "ACCOUNT_UNLOCKED event should be recorded");

        // User can now log in successfully
        AuthRequestDTO credentials = new AuthRequestDTO();
        credentials.setEmail(targetUser.getEmail());
        credentials.setPassword("TargetPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isOk());
    }

    @Test
    public void adminCanManuallyLockAccount_preventingLogin() throws Exception {
        mockMvc.perform(put("/api/users/" + targetUser.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("reason", "Suspicious privilege activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNonLocked").value(false));

        User lockedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertFalse(lockedUser.isAccountNonLocked());

        // Verify login is blocked
        AuthRequestDTO credentials = new AuthRequestDTO();
        credentials.setEmail(targetUser.getEmail());
        credentials.setPassword("TargetPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Account Locked"));
    }

    @Test
    public void expiredLockoutDuration_automaticallyUnlocksUserOnNextLogin() throws Exception {
        // Lock user with expiration set 1 minute in the past
        targetUser.setAccountNonLocked(false);
        targetUser.setFailedLoginAttempts(5);
        targetUser.setLockExpiresAt(LocalDateTime.now().minusMinutes(1));
        userRepository.save(targetUser);

        // Login with correct credentials
        AuthRequestDTO credentials = new AuthRequestDTO();
        credentials.setEmail(targetUser.getEmail());
        credentials.setPassword("TargetPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        User autoUnlockedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertTrue(autoUnlockedUser.isAccountNonLocked(), "Account should be automatically unlocked");
        assertEquals(0, autoUnlockedUser.getFailedLoginAttempts(), "Failed login attempts should be reset");
        assertNull(autoUnlockedUser.getLockExpiresAt(), "Lock expiration should be cleared");
    }

    @Test
    public void nonAdminUser_cannotLockOrUnlockAccounts_returnsForbidden() throws Exception {
        mockMvc.perform(put("/api/users/" + targetUser.getId() + "/unlock")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/" + targetUser.getId() + "/lock")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    public void adminCanFilterUsersByLockoutStatus() throws Exception {
        // Lock target user
        targetUser.setAccountNonLocked(false);
        userRepository.save(targetUser);

        // Query locked users
        mockMvc.perform(get("/api/users")
                        .param("locked", "true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value(targetUser.getEmail()))
                .andExpect(jsonPath("$.content[0].accountNonLocked").value(false));

        // Query unlocked users
        mockMvc.perform(get("/api/users")
                        .param("locked", "false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2)); // adminUser and regularUser
    }

    @org.junit.jupiter.api.AfterEach
    public void cleanup() {
        transactionReviewRepository.deleteAll();
        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        userRepository.deleteAll();
    }
}
