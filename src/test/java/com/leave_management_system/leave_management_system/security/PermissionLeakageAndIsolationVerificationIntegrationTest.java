package com.leave_management_system.leave_management_system.security;

import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.repository.PermissionRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Priority 2 — Verification of Permission Isolation & Cross-Test Leakage Prevention")
public class PermissionLeakageAndIsolationVerificationIntegrationTest {

    @Autowired
    private RolePermissionIsolationTestHelper permissionHelper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @BeforeEach
    public void setUp() {
        permissionHelper.resetToCanonicalPermissions();
    }

    @AfterEach
    public void tearDown() {
        permissionHelper.resetToCanonicalPermissions();
    }

    @Test
    @DisplayName("Every test establishes its exact expected role-permission set without depending on previous state")
    @Transactional
    void testEstablishExactRolePermissions_OverwritesLingeringState() {
        // 1. Simulate dirty state left by an earlier poorly-isolated test
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Permission leak1 = permissionRepository.findByName("LEAVE_APPROVE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_APPROVE", "leaked")));
        Permission leak2 = permissionRepository.findByName("ROLE_PERMISSION_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("ROLE_PERMISSION_MANAGE", "leaked")));
        empRole.getPermissions().addAll(Set.of(leak1, leak2));
        roleRepository.saveAndFlush(empRole);

        // Verify dirty state exists before isolation
        Role dirtyRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        assertTrue(dirtyRole.getPermissions().stream().anyMatch(p -> p.getName().equals("LEAVE_APPROVE")));

        // 2. Establish exact expected role-permission set
        Set<String> expectedSet = Set.of("LEAVE_CREATE", "LEAVE_CANCEL");
        permissionHelper.establishExactRolePermissions("EMPLOYEE", expectedSet);

        // 3. Verify exact set is established and lingering dirty permissions are completely purged
        Role isolatedRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Set<String> activePermNames = isolatedRole.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());

        assertEquals(expectedSet, activePermNames, "Active permissions must match strictly the specified expected set");
        assertFalse(activePermNames.contains("LEAVE_APPROVE"), "Leaked LEAVE_APPROVE must be purged");
        assertFalse(activePermNames.contains("ROLE_PERMISSION_MANAGE"), "Leaked ROLE_PERMISSION_MANAGE must be purged");
    }

    @Test
    @DisplayName("Restore modified role permissions after test execution guarantees canonical baseline")
    void testRestoreModifiedRolePermissionsAfterTest() {
        // 1. Mutate permissions on EMPLOYEE and MANAGER
        permissionHelper.establishExactRolePermissions("EMPLOYEE", Set.of("LEAVE_APPROVE", "LEAVE_VIEW_ALL"));
        permissionHelper.establishExactRolePermissions("MANAGER", Set.of("DEPARTMENT_MANAGE"));

        // 2. Execute restoration procedure (as run in @AfterEach)
        permissionHelper.resetToCanonicalPermissions();

        // 3. Verify EMPLOYEE role has strictly canonical permissions and zero privileged access
        permissionHelper.assertEmployeeCannotAccessAdminOrManagerPermissions();
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Set<String> empPerms = empRole.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());

        assertTrue(empPerms.contains("LEAVE_CREATE"));
        assertTrue(empPerms.contains("LEAVE_CANCEL"));
        assertFalse(empPerms.contains("LEAVE_APPROVE"));
        assertFalse(empPerms.contains("LEAVE_VIEW_ALL"));

        // 4. Verify MANAGER role has strictly canonical permissions
        Role mgrRole = roleRepository.findByName("MANAGER").orElseThrow();
        Set<String> mgrPerms = mgrRole.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
        assertTrue(mgrPerms.contains("LEAVE_APPROVE"));
        assertTrue(mgrPerms.contains("LEAVE_VIEW_TEAM"));
        assertFalse(mgrPerms.contains("DEPARTMENT_MANAGE"));
    }

    @Test
    @DisplayName("Verify no permission leakage between simulated test classes A and B")
    void testCrossTestClassLeakagePrevention() {
        // --- Class A Simulation ---
        // Class A elevates EMPLOYEE role for internal test
        permissionHelper.establishExactRolePermissions("EMPLOYEE", Set.of("LEAVE_CREATE", "LEAVE_APPROVE", "LEAVE_VIEW_ALL"));
        // Class A @AfterEach cleans up
        permissionHelper.resetToCanonicalPermissions();

        // --- Class B Simulation ---
        // Class B loads EMPLOYEE from database and asserts no leakage occurred
        Role freshRoleInClassB = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Set<String> activePermissions = freshRoleInClassB.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());

        assertFalse(activePermissions.contains("LEAVE_APPROVE"),
                "Class B must not see LEAVE_APPROVE leaked from Class A");
        assertFalse(activePermissions.contains("LEAVE_VIEW_ALL"),
                "Class B must not see LEAVE_VIEW_ALL leaked from Class A");

        permissionHelper.assertEmployeeCannotAccessAdminOrManagerPermissions();
    }

    @Test
    @DisplayName("Snapshot and restore role permissions retains complete fidelity")
    void testRolePermissionsSnapshotAndRestore() {
        // 1. Snapshot canonical state
        Map<String, Set<String>> snapshot = permissionHelper.snapshotCurrentPermissions();
        assertNotNull(snapshot);
        assertTrue(snapshot.containsKey("ADMIN"));
        assertTrue(snapshot.containsKey("EMPLOYEE"));

        // 2. Corrupt role state
        permissionHelper.establishExactRolePermissions("EMPLOYEE", Set.of());
        permissionHelper.establishExactRolePermissions("ADMIN", Set.of("LEAVE_CREATE"));

        assertEquals(0, roleRepository.findByName("EMPLOYEE").orElseThrow().getPermissions().size());

        // 3. Restore snapshot
        permissionHelper.restorePermissionsSnapshot(snapshot);

        // 4. Assert fidelity
        Role restoredEmp = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Set<String> restoredEmpPerms = restoredEmp.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
        assertEquals(snapshot.get("EMPLOYEE"), restoredEmpPerms);

        Role restoredAdmin = roleRepository.findByName("ADMIN").orElseThrow();
        Set<String> restoredAdminPerms = restoredAdmin.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
        assertEquals(snapshot.get("ADMIN"), restoredAdminPerms);
    }

    @Test
    @DisplayName("Assert EMPLOYEE possesses none of the 13 privileged operations")
    void testEmployeePossessesNoPrivilegedOperations() {
        permissionHelper.assertEmployeeCannotAccessAdminOrManagerPermissions();

        Role empRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Set<String> activePerms = empRole.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());

        for (String privileged : RolePermissionIsolationTestHelper.PRIVILEGED_OPERATIONS) {
            assertFalse(activePerms.contains(privileged),
                    "EMPLOYEE must not have privileged permission: " + privileged);
        }
    }
}
