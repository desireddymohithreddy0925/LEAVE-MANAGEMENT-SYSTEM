package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.PermissionResponseDTO;
import com.leave_management_system.leave_management_system.dto.RoleResponseDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.service.RolePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RolePermissionControllerTest {

    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private RolePermissionController rolePermissionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(rolePermissionController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .build();
    }

    @Test
    @DisplayName("GET /api/permissions - Returns all permissions")
    void getAllPermissions_Success() throws Exception {
        PermissionResponseDTO p1 = new PermissionResponseDTO(1L, "LEAVE_CREATE", "Permission to create leave", true);
        PermissionResponseDTO p2 = new PermissionResponseDTO(2L, "LEAVE_APPROVE", "Permission to approve leave", true);

        when(rolePermissionService.getAllPermissions()).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("LEAVE_CREATE"))
                .andExpect(jsonPath("$[1].name").value("LEAVE_APPROVE"));

        verify(rolePermissionService).getAllPermissions();
    }

    @Test
    @DisplayName("GET /api/roles - Returns all roles")
    void getAllRoles_Success() throws Exception {
        RoleResponseDTO r1 = new RoleResponseDTO();
        r1.setId(1L);
        r1.setName("ROLE_ADMIN");

        RoleResponseDTO r2 = new RoleResponseDTO();
        r2.setId(2L);
        r2.setName("ROLE_EMPLOYEE");

        when(rolePermissionService.getAllRoles()).thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$[1].name").value("ROLE_EMPLOYEE"));

        verify(rolePermissionService).getAllRoles();
    }

    @Test
    @DisplayName("GET /api/roles/{roleId}/permissions - Returns permissions for role")
    void getRolePermissions_Success() throws Exception {
        PermissionResponseDTO p1 = new PermissionResponseDTO(1L, "LEAVE_CREATE", "Create leave", true);

        when(rolePermissionService.getRolePermissions(1L)).thenReturn(List.of(p1));

        mockMvc.perform(get("/api/roles/1/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("LEAVE_CREATE"));

        verify(rolePermissionService).getRolePermissions(1L);
    }

    @Test
    @DisplayName("GET /api/roles/{roleId}/permissions - Role not found returns 404")
    void getRolePermissions_NotFound() throws Exception {
        when(rolePermissionService.getRolePermissions(99L))
                .thenThrow(new ResourceNotFoundException("Role not found with id: 99"));

        mockMvc.perform(get("/api/roles/99/permissions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(rolePermissionService).getRolePermissions(99L);
    }

    @Test
    @DisplayName("PUT /api/roles/{roleId}/permissions - Replaces role permissions")
    void replaceRolePermissions_Success() throws Exception {
        List<Long> permissionIds = List.of(1L, 2L);
        PermissionResponseDTO p1 = new PermissionResponseDTO(1L, "LEAVE_CREATE", "Create leave", true);
        PermissionResponseDTO p2 = new PermissionResponseDTO(2L, "LEAVE_APPROVE", "Approve leave", true);

        when(rolePermissionService.replaceRolePermissions(eq(1L), anyList())).thenReturn(List.of(p1, p2));

        mockMvc.perform(put("/api/roles/1/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(permissionIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("LEAVE_CREATE"));

        verify(rolePermissionService).replaceRolePermissions(eq(1L), eq(permissionIds));
    }

    @Test
    @DisplayName("POST /api/roles/{roleId}/permissions/{permissionId} - Adds permission to role")
    void addPermissionToRole_Success() throws Exception {
        PermissionResponseDTO p1 = new PermissionResponseDTO(5L, "AUDIT_VIEW", "View audit", true);

        when(rolePermissionService.addPermissionToRole(1L, 5L)).thenReturn(List.of(p1));

        mockMvc.perform(post("/api/roles/1/permissions/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("AUDIT_VIEW"));

        verify(rolePermissionService).addPermissionToRole(1L, 5L);
    }

    @Test
    @DisplayName("DELETE /api/roles/{roleId}/permissions/{permissionId} - Removes permission from role")
    void removePermissionFromRole_Success() throws Exception {
        when(rolePermissionService.removePermissionFromRole(1L, 5L)).thenReturn(List.of());

        mockMvc.perform(delete("/api/roles/1/permissions/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(rolePermissionService).removePermissionFromRole(1L, 5L);
    }
}

