package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.PermissionResponseDTO;
import com.leave_management_system.leave_management_system.dto.RoleResponseDTO;
import com.leave_management_system.leave_management_system.service.RolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Role and Permission Management", description = "Endpoints for managing roles and their permissions")
@SecurityRequirement(name = "bearerAuth")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    public RolePermissionController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE') or hasRole('ADMIN')")
    @Operation(summary = "Get all permissions")
    public ResponseEntity<List<PermissionResponseDTO>> getAllPermissions() {
        return ResponseEntity.ok(rolePermissionService.getAllPermissions());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE') or hasRole('ADMIN')")
    @Operation(summary = "Get all roles")
    public ResponseEntity<List<RoleResponseDTO>> getAllRoles() {
        return ResponseEntity.ok(rolePermissionService.getAllRoles());
    }

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE') or hasRole('ADMIN')")
    @Operation(summary = "Get permissions for a specific role")
    public ResponseEntity<List<PermissionResponseDTO>> getRolePermissions(@PathVariable Long roleId) {
        return ResponseEntity.ok(rolePermissionService.getRolePermissions(roleId));
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE') or hasRole('ADMIN')")
    @Operation(summary = "Replace all permissions for a specific role")
    public ResponseEntity<List<PermissionResponseDTO>> replaceRolePermissions(
            @PathVariable Long roleId,
            @RequestBody List<Long> permissionIds) {
        return ResponseEntity.ok(rolePermissionService.replaceRolePermissions(roleId, permissionIds));
    }

    @PostMapping("/roles/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE') or hasRole('ADMIN')")
    @Operation(summary = "Add a single permission to a role")
    public ResponseEntity<List<PermissionResponseDTO>> addPermissionToRole(
            @PathVariable Long roleId,
            @PathVariable Long permissionId) {
        return ResponseEntity.ok(rolePermissionService.addPermissionToRole(roleId, permissionId));
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('ROLE_PERMISSION_MANAGE') or hasRole('ADMIN')")
    @Operation(summary = "Remove a single permission from a role")
    public ResponseEntity<List<PermissionResponseDTO>> removePermissionFromRole(
            @PathVariable Long roleId,
            @PathVariable Long permissionId) {
        return ResponseEntity.ok(rolePermissionService.removePermissionFromRole(roleId, permissionId));
    }
}
