package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.PermissionResponseDTO;
import com.leave_management_system.leave_management_system.dto.RoleResponseDTO;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.PermissionRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RolePermissionServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private AuthorizationAuditService authorizationAuditService;

    @InjectMocks
    private RolePermissionService rolePermissionService;

    private Role testRole;
    private Permission perm1;
    private Permission perm2;

    @BeforeEach
    void setUp() {
        perm1 = new Permission("LEAVE_APPLY", "Can apply for leave");
        perm1.setId(101L);

        perm2 = new Permission("LEAVE_APPROVE", "Can approve leave");
        perm2.setId(102L);

        testRole = new Role("MANAGER");
        testRole.setId(1L);
        testRole.getPermissions().add(perm1);
    }

    @Test
    @DisplayName("getAllPermissions: returns all mapped permissions")
    void getAllPermissions_Success() {
        when(permissionRepository.findAll()).thenReturn(List.of(perm1, perm2));

        List<PermissionResponseDTO> list = rolePermissionService.getAllPermissions();

        assertEquals(2, list.size());
        assertEquals("LEAVE_APPLY", list.get(0).getName());
        assertEquals("LEAVE_APPROVE", list.get(1).getName());
    }

    @Test
    @DisplayName("getAllRoles: returns all mapped roles")
    void getAllRoles_Success() {
        when(roleRepository.findAll()).thenReturn(List.of(testRole));

        List<RoleResponseDTO> list = rolePermissionService.getAllRoles();

        assertEquals(1, list.size());
        assertEquals("MANAGER", list.get(0).getName());
    }

    @Test
    @DisplayName("getRolePermissions: returns permissions belonging to role")
    void getRolePermissions_Success() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));

        List<PermissionResponseDTO> permissions = rolePermissionService.getRolePermissions(1L);

        assertEquals(1, permissions.size());
        assertEquals("LEAVE_APPLY", permissions.get(0).getName());
    }

    @Test
    @DisplayName("getRolePermissions: throws ResourceNotFoundException if role not found")
    void getRolePermissions_RoleNotFound_ThrowsException() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> rolePermissionService.getRolePermissions(99L));
    }

    @Test
    @DisplayName("replaceRolePermissions: replaces existing permissions with new set")
    void replaceRolePermissions_Success() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findAllById(List.of(102L))).thenReturn(List.of(perm2));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        List<PermissionResponseDTO> result = rolePermissionService.replaceRolePermissions(1L, List.of(102L));

        assertEquals(1, result.size());
        assertEquals("LEAVE_APPROVE", result.get(0).getName());
        verify(auditService, times(1)).logAction(eq("ROLE_PERMISSIONS_REPLACED"), eq("Role"), eq(1L), isNull(), anyString());
    }

    @Test
    @DisplayName("replaceRolePermissions: throws ResourceNotFoundException if any permission ID is missing")
    void replaceRolePermissions_MissingPermissionId_ThrowsException() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findAllById(List.of(102L, 999L))).thenReturn(List.of(perm2));

        assertThrows(ResourceNotFoundException.class, () -> rolePermissionService.replaceRolePermissions(1L, List.of(102L, 999L)));
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("addPermissionToRole: adds permission to role successfully")
    void addPermissionToRole_Success() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findById(102L)).thenReturn(Optional.of(perm2));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        List<PermissionResponseDTO> result = rolePermissionService.addPermissionToRole(1L, 102L);

        assertEquals(2, result.size());
        assertTrue(testRole.getPermissions().contains(perm2));
        verify(auditService, times(1)).logAction(eq("ROLE_PERMISSION_ADDED"), eq("Role"), eq(1L), isNull(), anyString());
    }

    @Test
    @DisplayName("removePermissionFromRole: removes permission from role successfully")
    void removePermissionFromRole_Success() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findById(101L)).thenReturn(Optional.of(perm1));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        List<PermissionResponseDTO> result = rolePermissionService.removePermissionFromRole(1L, 101L);

        assertEquals(0, result.size());
        assertFalse(testRole.getPermissions().contains(perm1));
        verify(auditService, times(1)).logAction(eq("ROLE_PERMISSION_REMOVED"), eq("Role"), eq(1L), isNull(), anyString());
    }
}

