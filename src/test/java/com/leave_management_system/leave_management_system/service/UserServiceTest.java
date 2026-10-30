package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.UserResponseDTO;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.SecurityEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("user@example.com");
        testUser.setActive(true);
        testUser.setAccountNonLocked(true);
        testUser.setFailedLoginAttempts(0);
    }

    @Test
    @DisplayName("getAllUsers: returns page of user response DTOs")
    void getAllUsers_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(testUser), pageable, 1);

        when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(userPage);

        Page<UserResponseDTO> result = userService.getAllUsers(null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("user@example.com", result.getContent().get(0).getEmail());
    }

    @Test
    @DisplayName("getUserById: returns user when found")
    void getUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserResponseDTO dto = userService.getUserById(1L);

        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals("user@example.com", dto.getEmail());
    }

    @Test
    @DisplayName("getUserById: throws ResourceNotFoundException when user not found")
    void getUserById_NotFound_ThrowsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(999L));
    }

    @Test
    @DisplayName("unlockUser: resets lock, failed attempts, and logs security event")
    void unlockUser_Success() {
        testUser.setAccountNonLocked(false);
        testUser.setFailedLoginAttempts(5);
        testUser.setLockExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO dto = userService.unlockUser(1L);

        assertNotNull(dto);
        assertTrue(testUser.isAccountNonLocked());
        assertEquals(0, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLockExpiresAt());
        verify(userRepository, times(1)).save(testUser);
        verify(auditService, times(1)).logSecurityEvent(
                eq(SecurityEventType.ACCOUNT_UNLOCKED),
                isNull(),
                eq("user@example.com"),
                anyString()
        );
    }

    @Test
    @DisplayName("unlockUser: throws ResourceNotFoundException if user does not exist")
    void unlockUser_NotFound_ThrowsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.unlockUser(999L));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("lockUser: sets accountNonLocked to false, records audit log and security event")
    void lockUser_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO dto = userService.lockUser(1L, "Security violation");

        assertNotNull(dto);
        assertFalse(testUser.isAccountNonLocked());
        verify(userRepository, times(1)).save(testUser);
        verify(auditService, times(1)).logSecurityEvent(
                eq(SecurityEventType.ACCOUNT_LOCKED),
                isNull(),
                eq("user@example.com"),
                contains("Security violation")
        );
    }

    @Test
    @DisplayName("lockUser: uses default reason when reason is blank or null")
    void lockUser_BlankReason_UsesDefault() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.lockUser(1L, "   ");

        assertFalse(testUser.isAccountNonLocked());
        verify(auditService, times(1)).logSecurityEvent(
                eq(SecurityEventType.ACCOUNT_LOCKED),
                isNull(),
                eq("user@example.com"),
                contains("Manually locked by administrator")
        );
    }
}

