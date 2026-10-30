package com.leave_management_system.leave_management_system.security;

import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User testUser;
    private Role adminRole;
    private Permission permActive;
    private Permission permInactive;

    @BeforeEach
    void setUp() {
        permActive = new Permission("USER_VIEW", "View users");
        permActive.setActive(true);

        permInactive = new Permission("USER_DELETE", "Delete users");
        permInactive.setActive(false);

        adminRole = new Role("ADMIN");
        adminRole.getPermissions().add(permActive);
        adminRole.getPermissions().add(permInactive);

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("admin@example.com");
        testUser.setPassword("hashedPassword");
        testUser.setActive(true);
        testUser.setAccountNonLocked(true);
        testUser.setFailedLoginAttempts(0);
        testUser.getRoles().add(adminRole);
    }

    @Test
    @DisplayName("loadUserByUsername: maps roles and active permissions to GrantedAuthority")
    void loadUserByUsername_Success() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(testUser));

        UserDetails details = customUserDetailsService.loadUserByUsername("admin@example.com");

        assertNotNull(details);
        assertEquals("admin@example.com", details.getUsername());
        assertEquals("hashedPassword", details.getPassword());
        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonLocked());

        Set<String> authorities = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("USER_VIEW"));
        assertFalse(authorities.contains("USER_DELETE"), "Inactive permission should not be granted");
    }

    @Test
    @DisplayName("loadUserByUsername: throws UsernameNotFoundException when user is not found")
    void loadUserByUsername_UserNotFound_ThrowsException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("missing@example.com"));
    }

    @Test
    @DisplayName("loadUserByUsername: user is disabled if employee status is INACTIVE")
    void loadUserByUsername_EmployeeInactive_Disabled() {
        Employee employee = new Employee();
        employee.setStatus("INACTIVE");
        testUser.setEmployee(employee);

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(testUser));

        UserDetails details = customUserDetailsService.loadUserByUsername("admin@example.com");

        assertFalse(details.isEnabled());
    }

    @Test
    @DisplayName("loadUserByUsername: auto-unlocks account if lockExpiresAt has elapsed")
    void loadUserByUsername_ExpiredLock_AutoUnlocks() {
        testUser.setAccountNonLocked(false);
        testUser.setFailedLoginAttempts(5);
        testUser.setLockExpiresAt(LocalDateTime.now().minusMinutes(10)); // expired

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDetails details = customUserDetailsService.loadUserByUsername("admin@example.com");

        assertTrue(details.isAccountNonLocked());
        assertTrue(testUser.isAccountNonLocked());
        assertEquals(0, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLockExpiresAt());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("loadUserByUsername: preserves locked status if lockExpiresAt is in the future")
    void loadUserByUsername_ActiveLock_StaysLocked() {
        testUser.setAccountNonLocked(false);
        testUser.setFailedLoginAttempts(5);
        testUser.setLockExpiresAt(LocalDateTime.now().plusMinutes(10)); // active

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(testUser));

        UserDetails details = customUserDetailsService.loadUserByUsername("admin@example.com");

        assertFalse(details.isAccountNonLocked());
        verify(userRepository, never()).save(any());
    }
}

