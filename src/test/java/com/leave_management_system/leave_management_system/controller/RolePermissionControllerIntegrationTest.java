package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.PermissionResponseDTO;
import com.leave_management_system.leave_management_system.dto.RoleResponseDTO;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class RolePermissionControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private String employeeToken;
    private Role adminRole;
    private Role managerRole;
    private Permission viewPermission;
    private Permission testPermission;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        
        viewPermission = permissionRepository.findByName("LEAVE_VIEW_ALL").orElseThrow();
        testPermission = permissionRepository.findByName("ROLE_PERMISSION_MANAGE").orElseThrow();
        
        // Setup Admin
        User adminUser = new User();
        adminUser.setEmail("admin@test.com");
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

        // Setup Employee
        User empUser = new User();
        empUser.setEmail("emp@test.com");
        empUser.setPassword("password");
        empUser.setActive(true);
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
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
    }

    @Test
    void testGetAllPermissions_asAdmin() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/permissions")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
                
        List<PermissionResponseDTO> permissions = objectMapper.readValue(
            result.getResponse().getContentAsString(), 
            new TypeReference<List<PermissionResponseDTO>>() {}
        );
        
        assertThat(permissions).isNotEmpty();
    }

    @Test
    void testGetAllPermissions_asEmployee_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/permissions")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetRolePermissions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/roles/" + managerRole.getId() + "/permissions")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
                
        List<PermissionResponseDTO> permissions = objectMapper.readValue(
            result.getResponse().getContentAsString(), 
            new TypeReference<List<PermissionResponseDTO>>() {}
        );
        
        assertThat(permissions).isNotEmpty();
    }

    @Test
    void testAddPermissionToRole() throws Exception {
        Permission newPerm = permissionRepository.findByName("DEPARTMENT_MANAGE").orElseThrow();
        
        MvcResult result = mockMvc.perform(post("/api/roles/" + managerRole.getId() + "/permissions/" + newPerm.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
                
        List<PermissionResponseDTO> permissions = objectMapper.readValue(
            result.getResponse().getContentAsString(), 
            new TypeReference<List<PermissionResponseDTO>>() {}
        );
        
        assertThat(permissions).extracting(PermissionResponseDTO::getName).contains("DEPARTMENT_MANAGE");
    }

    @Test
    void testRemovePermissionFromRole() throws Exception {
        // First add it
        managerRole.getPermissions().add(viewPermission);
        roleRepository.save(managerRole);
        
        MvcResult result = mockMvc.perform(delete("/api/roles/" + managerRole.getId() + "/permissions/" + viewPermission.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
                
        List<PermissionResponseDTO> permissions = objectMapper.readValue(
            result.getResponse().getContentAsString(), 
            new TypeReference<List<PermissionResponseDTO>>() {}
        );
        
        assertThat(permissions).extracting(PermissionResponseDTO::getName).doesNotContain(viewPermission.getName());
    }

    @Test
    void testReplaceRolePermissions() throws Exception {
        List<Long> newPermIds = List.of(viewPermission.getId(), testPermission.getId());
        
        MvcResult result = mockMvc.perform(put("/api/roles/" + managerRole.getId() + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newPermIds)))
                .andExpect(status().isOk())
                .andReturn();
                
        List<PermissionResponseDTO> permissions = objectMapper.readValue(
            result.getResponse().getContentAsString(), 
            new TypeReference<List<PermissionResponseDTO>>() {}
        );
        
        assertThat(permissions).hasSize(2);
        assertThat(permissions).extracting(PermissionResponseDTO::getName)
            .containsExactlyInAnyOrder(viewPermission.getName(), testPermission.getName());
    }
}
