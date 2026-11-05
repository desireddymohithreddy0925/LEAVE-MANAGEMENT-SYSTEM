package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.PermissionRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class UserControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private User adminUser;
    private String adminToken;

    private User regularUser;
    private String regularUserToken;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        // 1. Setup Admin Role and User
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role r = new Role("ADMIN");
            return roleRepository.save(r);
        });

        Permission lockManagePerm = permissionRepository.findByName("USER_LOCK_MANAGE").orElseGet(() -> {
            Permission p = new Permission("USER_LOCK_MANAGE", "Lock and unlock user accounts");
            return permissionRepository.save(p);
        });
        adminRole.getPermissions().add(lockManagePerm);
        roleRepository.save(adminRole);

        adminUser = new User();
        adminUser.setEmail("admin.userctrl." + System.currentTimeMillis() + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.setRoles(new HashSet<>(Set.of(adminRole)));
        adminUser = userRepository.save(adminUser);
        adminToken = jwtUtils.generateTokenFromUsername(adminUser.getEmail());

        // 2. Setup Regular Employee Role and User
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> {
            Role r = new Role("EMPLOYEE");
            return roleRepository.save(r);
        });

        regularUser = new User();
        regularUser.setEmail("reg.userctrl." + System.currentTimeMillis() + "@test.com");
        regularUser.setPassword(passwordEncoder.encode("UserPass123!"));
        regularUser.setActive(true);
        regularUser.setAccountNonLocked(true);
        regularUser.setRoles(new HashSet<>(Set.of(employeeRole)));
        regularUser = userRepository.save(regularUser);
        regularUserToken = jwtUtils.generateTokenFromUsername(regularUser.getEmail());
    }

    private User createUser(String emailPrefix, boolean accountNonLocked) {
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        User u = new User();
        u.setEmail(emailPrefix + "." + System.currentTimeMillis() + "@test.com");
        u.setPassword(passwordEncoder.encode("TestPass123!"));
        u.setActive(true);
        u.setAccountNonLocked(accountNonLocked);
        u.setFailedLoginAttempts(accountNonLocked ? 0 : 5);
        u.setRoles(new HashSet<>(Set.of(employeeRole)));
        return userRepository.save(u);
    }

    @Test
    void getAllUsers_AsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/users")
                .param("size", "100")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.content[*].email", hasItem(adminUser.getEmail())));
    }

    @Test
    void getAllUsers_FilterLocked_True() throws Exception {
        User lockedUser = createUser("locked.user", false);
        User unlockedUser = createUser("unlocked.user", true);

        mockMvc.perform(get("/api/users")
                .param("size", "100")
                .header("Authorization", "Bearer " + adminToken)
                .param("locked", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(lockedUser.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(unlockedUser.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].accountNonLocked", everyItem(is(false))));
    }

    @Test
    void getAllUsers_FilterLocked_False() throws Exception {
        User lockedUser = createUser("locked.user2", false);
        User unlockedUser = createUser("unlocked.user2", true);

        mockMvc.perform(get("/api/users")
                .param("size", "100")
                .header("Authorization", "Bearer " + adminToken)
                .param("locked", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(unlockedUser.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(lockedUser.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].accountNonLocked", everyItem(is(true))));
    }

    @Test
    void getAllUsers_PaginationAndSorting() throws Exception {
        for (int i = 0; i < 5; i++) {
            createUser("page.user" + i, true);
        }

        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "3")
                .param("sort", "email,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void getAllUsers_AsRegularUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllUsers_Unauthenticated_UnauthorizedOrForbidden() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserById_AsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/users/" + regularUser.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(regularUser.getId()))
                .andExpect(jsonPath("$.email").value(regularUser.getEmail()))
                .andExpect(jsonPath("$.accountNonLocked").value(true));
    }

    @Test
    void getUserById_NotFound() throws Exception {
        mockMvc.perform(get("/api/users/999999")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserById_AsUnauthorizedUser_Forbidden() throws Exception {
        // regular user attempting to query admin user details
        mockMvc.perform(get("/api/users/" + adminUser.getId())
                .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockUser_AsAdmin_Success() throws Exception {
        User target = createUser("to.lock", true);
        assertTrue(target.isAccountNonLocked());

        mockMvc.perform(put("/api/users/" + target.getId() + "/lock")
                .header("Authorization", "Bearer " + adminToken)
                .param("reason", "Suspicious login detected from foreign IP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId()))
                .andExpect(jsonPath("$.accountNonLocked").value(false));

        User refreshed = userRepository.findById(target.getId()).orElseThrow();
        assertFalse(refreshed.isAccountNonLocked());
    }

    @Test
    void lockUser_WithoutReason_Success() throws Exception {
        User target = createUser("to.lock.noreason", true);

        mockMvc.perform(put("/api/users/" + target.getId() + "/lock")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId()))
                .andExpect(jsonPath("$.accountNonLocked").value(false));
    }

    @Test
    void lockUser_NotFound() throws Exception {
        mockMvc.perform(put("/api/users/999999/lock")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void lockUser_AsRegularUser_Forbidden() throws Exception {
        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/lock")
                .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unlockUser_AsAdmin_Success() throws Exception {
        User locked = createUser("to.unlock", false);
        assertFalse(locked.isAccountNonLocked());

        mockMvc.perform(put("/api/users/" + locked.getId() + "/unlock")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(locked.getId()))
                .andExpect(jsonPath("$.accountNonLocked").value(true))
                .andExpect(jsonPath("$.failedLoginAttempts").value(0));

        User refreshed = userRepository.findById(locked.getId()).orElseThrow();
        assertTrue(refreshed.isAccountNonLocked());
    }

    @Test
    void unlockUser_NotFound() throws Exception {
        mockMvc.perform(put("/api/users/999999/unlock")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unlockUser_AsRegularUser_Forbidden() throws Exception {
        User locked = createUser("regular.try.unlock", false);

        mockMvc.perform(put("/api/users/" + locked.getId() + "/unlock")
                .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden());
    }
}

