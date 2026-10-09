package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.PermissionResponseDTO;
import com.leave_management_system.leave_management_system.dto.RoleResponseDTO;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.PermissionRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    public RolePermissionService(RoleRepository roleRepository, PermissionRepository permissionRepository, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;
    }

    public List<PermissionResponseDTO> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(PermissionResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<RoleResponseDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(RoleResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<PermissionResponseDTO> getRolePermissions(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        return role.getPermissions().stream()
                .map(PermissionResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<PermissionResponseDTO> replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        
        List<Permission> newPermissions = permissionRepository.findAllById(permissionIds);
        role.getPermissions().clear();
        role.getPermissions().addAll(newPermissions);
        
        Role updatedRole = roleRepository.save(role);
        
        auditService.logAction("ROLE_PERMISSIONS_REPLACED", "Role", role.getId(), null, "Replaced with " + permissionIds.size() + " permissions");
        
        return updatedRole.getPermissions().stream()
                .map(PermissionResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<PermissionResponseDTO> addPermissionToRole(Long roleId, Long permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));
                
        role.getPermissions().add(permission);
        Role updatedRole = roleRepository.save(role);
        
        auditService.logAction("ROLE_PERMISSION_ADDED", "Role", role.getId(), null, "Added permission: " + permission.getName());
        
        return updatedRole.getPermissions().stream()
                .map(PermissionResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<PermissionResponseDTO> removePermissionFromRole(Long roleId, Long permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));
                
        role.getPermissions().remove(permission);
        Role updatedRole = roleRepository.save(role);
        
        auditService.logAction("ROLE_PERMISSION_REMOVED", "Role", role.getId(), null, "Removed permission: " + permission.getName());
        
        return updatedRole.getPermissions().stream()
                .map(PermissionResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
