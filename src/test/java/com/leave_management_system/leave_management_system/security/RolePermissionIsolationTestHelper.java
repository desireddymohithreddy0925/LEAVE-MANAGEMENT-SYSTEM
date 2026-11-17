package com.leave_management_system.leave_management_system.security;

import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.repository.PermissionRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Priority 2 — Permission Isolation Helper for Integration and E2E Tests.
 *
 * Guarantees that:
 * 1. Every test establishes its exact expected role-permission set without depending
 *    on permissions left behind by previously executed tests.
 * 2. Modified role permissions are safely snapshotted and restored to their canonical
 *    baseline after each test.
 * 3. Prevents permission leakage between test classes.
 */
@Component
public class RolePermissionIsolationTestHelper {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionIsolationTestHelper.class);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Canonical baseline permissions per role as defined in Liquibase changesets:
     * 014-add-permissions.yaml and 021-add-security-management-permissions.yaml.
     */
    public static final Set<String> CANONICAL_EMPLOYEE_PERMISSIONS = Set.of(
            "LEAVE_VIEW_SELF",
            "LEAVE_VIEW_OWN",
            "LEAVE_CREATE",
            "LEAVE_CANCEL",
            "LEAVE_BALANCE_VIEW"
    );

    public static final Set<String> CANONICAL_MANAGER_PERMISSIONS = Set.of(
            "EMPLOYEE_VIEW",
            "DEPARTMENT_VIEW",
            "LEAVE_VIEW_TEAM",
            "LEAVE_APPROVE",
            "LEAVE_REJECT",
            "LEAVE_CREATE",
            "LEAVE_CANCEL",
            "LEAVE_BALANCE_VIEW",
            "LEAVE_VIEW_SELF",
            "LEAVE_VIEW_OWN"
    );

    public static final Set<String> CANONICAL_HR_PERMISSIONS = Set.of(
            "EMPLOYEE_VIEW",
            "EMPLOYEE_CREATE",
            "EMPLOYEE_UPDATE",
            "EMPLOYEE_DEACTIVATE",
            "DEPARTMENT_VIEW",
            "DEPARTMENT_MANAGE",
            "LEAVE_VIEW_ALL",
            "LEAVE_CREATE",
            "LEAVE_APPROVE",
            "LEAVE_REJECT",
            "LEAVE_CANCEL",
            "LEAVE_BALANCE_VIEW",
            "LEAVE_BALANCE_MANAGE",
            "LEAVE_TYPE_VIEW",
            "LEAVE_TYPE_MANAGE"
    );

    public static final Set<String> CANONICAL_ADMIN_PERMISSIONS = Set.of(
            "EMPLOYEE_VIEW",
            "EMPLOYEE_CREATE",
            "EMPLOYEE_UPDATE",
            "EMPLOYEE_DEACTIVATE",
            "DEPARTMENT_VIEW",
            "DEPARTMENT_MANAGE",
            "LEAVE_VIEW_SELF",
            "LEAVE_VIEW_OWN",
            "LEAVE_VIEW_TEAM",
            "LEAVE_VIEW_ALL",
            "LEAVE_CREATE",
            "LEAVE_APPROVE",
            "LEAVE_REJECT",
            "LEAVE_CANCEL",
            "LEAVE_BALANCE_VIEW",
            "LEAVE_BALANCE_MANAGE",
            "LEAVE_TYPE_VIEW",
            "LEAVE_TYPE_MANAGE",
            "ROLE_PERMISSION_MANAGE",
            "SECURITY_EVENT_MANAGE",
            "AUDIT_LOG_VIEW",
            "ADMIN_VIEW",
            "USER_LOCK_MANAGE"
    );

    public static final Set<String> CANONICAL_SECURITY_OFFICER_PERMISSIONS = Set.of(
            "SECURITY_EVENT_MANAGE",
            "AUDIT_LOG_VIEW",
            "ADMIN_VIEW"
    );

    /**
     * Set of administrative and manager operations that an EMPLOYEE must NEVER possess.
     */
    public static final Set<String> PRIVILEGED_OPERATIONS = Set.of(
            "LEAVE_APPROVE",
            "LEAVE_REJECT",
            "LEAVE_VIEW_ALL",
            "LEAVE_BALANCE_MANAGE",
            "DEPARTMENT_MANAGE",
            "EMPLOYEE_CREATE",
            "EMPLOYEE_UPDATE",
            "EMPLOYEE_DEACTIVATE",
            "ROLE_PERMISSION_MANAGE",
            "USER_LOCK_MANAGE",
            "SECURITY_EVENT_MANAGE",
            "AUDIT_LOG_VIEW",
            "ADMIN_VIEW"
    );

    public RolePermissionIsolationTestHelper(RoleRepository roleRepository,
                                             PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    /**
     * Resets all primary roles (ADMIN, HR, MANAGER, EMPLOYEE, SECURITY_OFFICER)
     * to their canonical baseline permission sets.
     */
    @Transactional
    public void resetToCanonicalPermissions() {
        establishExactRolePermissions("EMPLOYEE", CANONICAL_EMPLOYEE_PERMISSIONS);
        establishExactRolePermissions("MANAGER", CANONICAL_MANAGER_PERMISSIONS);
        establishExactRolePermissions("HR", CANONICAL_HR_PERMISSIONS);
        establishExactRolePermissions("ADMIN", CANONICAL_ADMIN_PERMISSIONS);

        roleRepository.findByName("SECURITY_OFFICER").ifPresent(role ->
                establishExactRolePermissions("SECURITY_OFFICER", CANONICAL_SECURITY_OFFICER_PERMISSIONS));

        log.debug("[PERMISSION-ISOLATION] All roles successfully restored to canonical baseline.");
    }

    /**
     * Strictly establishes the exact expected permission set for a given role,
     * clearing any previous or lingering permissions.
     */
    @Transactional
    public Role establishExactRolePermissions(String roleName, Collection<String> permissionNames) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));

        Set<Permission> targetPermissions = new HashSet<>();
        for (String permName : permissionNames) {
            Permission permission = permissionRepository.findByName(permName)
                    .orElseGet(() -> permissionRepository.save(new Permission(permName, permName + " permission")));
            targetPermissions.add(permission);
        }

        role.getPermissions().clear();
        role.getPermissions().addAll(targetPermissions);
        Role saved = roleRepository.saveAndFlush(role);

        log.debug("[PERMISSION-ISOLATION] Role '{}' strictly configured with {} permissions: {}",
                roleName, targetPermissions.size(), permissionNames);
        return saved;
    }

    /**
     * Takes an in-memory snapshot of all current role permissions.
     */
    @Transactional(readOnly = true)
    public Map<String, Set<String>> snapshotCurrentPermissions() {
        Map<String, Set<String>> snapshot = new HashMap<>();
        for (Role role : roleRepository.findAll()) {
            Set<String> perms = role.getPermissions().stream()
                    .map(Permission::getName)
                    .collect(Collectors.toSet());
            snapshot.put(role.getName(), perms);
        }
        return snapshot;
    }

    /**
     * Restores role permissions from a previously captured snapshot.
     */
    @Transactional
    public void restorePermissionsSnapshot(Map<String, Set<String>> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            resetToCanonicalPermissions();
            return;
        }

        for (Map.Entry<String, Set<String>> entry : snapshot.entrySet()) {
            establishExactRolePermissions(entry.getKey(), entry.getValue());
        }
        log.debug("[PERMISSION-ISOLATION] Restored role permissions from snapshot ({} roles).", snapshot.size());
    }

    /**
     * Asserts that the EMPLOYEE role in the database does not contain any privileged
     * administrative or manager permissions.
     */
    @Transactional(readOnly = true)
    public void assertEmployeeCannotAccessAdminOrManagerPermissions() {
        Role empRole = roleRepository.findByName("EMPLOYEE")
                .orElseThrow(() -> new AssertionError("EMPLOYEE role not found in database"));

        Set<String> activePermissions = empRole.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());

        for (String privileged : PRIVILEGED_OPERATIONS) {
            assertFalse(activePermissions.contains(privileged),
                    String.format("Permission leakage detected! EMPLOYEE possesses privileged permission '%s'. Active permissions: %s",
                            privileged, activePermissions));
        }
    }

    /**
     * Asserts that a role contains strictly the allowed permissions.
     */
    @Transactional(readOnly = true)
    public void assertRoleHasOnlyAllowedPermissions(String roleName, Set<String> allowedPermissions) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AssertionError("Role '" + roleName + "' not found"));

        Set<String> activePermissions = role.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());

        for (String active : activePermissions) {
            assertTrue(allowedPermissions.contains(active),
                    String.format("Role '%s' has unauthorized leaked permission '%s'. Active: %s, Allowed: %s",
                            roleName, active, activePermissions, allowedPermissions));
        }
    }
}
