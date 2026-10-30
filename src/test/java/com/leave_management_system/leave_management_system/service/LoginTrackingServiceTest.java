package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LoginHistoryResponseDTO;
import com.leave_management_system.leave_management_system.dto.LoginSummaryDTO;
import com.leave_management_system.leave_management_system.entity.LoginHistory;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.LoginHistoryRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoginTrackingServiceTest {

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private LoginTrackingService loginTrackingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(loginTrackingService, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(loginTrackingService, "lockoutDurationMinutes", 15);
        ReflectionTestUtils.setField(loginTrackingService, "auditService", auditService);

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("tracker@example.com");
        testUser.setAccountNonLocked(true);
        testUser.setFailedLoginAttempts(0);
        testUser.setLoginCount(10L);
    }

    @Test
    @DisplayName("recordLoginSuccess: updates user metadata, increments count, and logs history")
    void recordLoginSuccess_Success() {
        when(userRepository.findByEmail("tracker@example.com")).thenReturn(Optional.of(testUser));

        loginTrackingService.recordLoginSuccess("tracker@example.com", "192.168.1.10", "Mozilla/5.0");

        assertEquals(11L, testUser.getLoginCount());
        assertEquals("192.168.1.10", testUser.getLastLoginIp());
        assertEquals(0, testUser.getFailedLoginAttempts());
        assertNull(testUser.getLockExpiresAt());
        assertTrue(testUser.isAccountNonLocked());
        verify(userRepository, times(1)).save(testUser);
        verify(loginHistoryRepository, times(1)).save(any(LoginHistory.class));
    }

    @Test
    @DisplayName("recordLoginFailure: increments failed login attempts")
    void recordLoginFailure_IncrementsAttempts() {
        when(userRepository.findByEmail("tracker@example.com")).thenReturn(Optional.of(testUser));

        loginTrackingService.recordLoginFailure("tracker@example.com", "192.168.1.10", "Mozilla/5.0", "Bad credentials");

        assertEquals(1, testUser.getFailedLoginAttempts());
        assertTrue(testUser.isAccountNonLocked());
        verify(userRepository, times(1)).save(testUser);
        verify(loginHistoryRepository, times(1)).save(any(LoginHistory.class));
        verify(auditService, never()).logSecurityEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("recordLoginFailure: locks account and sets expiration when maxFailedAttempts is reached")
    void recordLoginFailure_ReachesMaxAttempts_LocksAccount() {
        testUser.setFailedLoginAttempts(4);
        when(userRepository.findByEmail("tracker@example.com")).thenReturn(Optional.of(testUser));

        loginTrackingService.recordLoginFailure("tracker@example.com", "192.168.1.10", "Mozilla/5.0", "Bad credentials");

        assertEquals(5, testUser.getFailedLoginAttempts());
        assertFalse(testUser.isAccountNonLocked());
        assertNotNull(testUser.getLockExpiresAt());
        assertTrue(testUser.getLockExpiresAt().isAfter(LocalDateTime.now()));
        verify(userRepository, times(1)).save(testUser);
        verify(auditService, times(1)).logSecurityEvent(
                eq(SecurityEventType.ACCOUNT_LOCKED),
                eq("192.168.1.10"),
                eq("tracker@example.com"),
                contains("Account locked after 5 consecutive failed login attempts")
        );
    }

    @Test
    @DisplayName("getLoginHistoryForUser: returns page of login history DTOs")
    void getLoginHistoryForUser_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        LoginHistory history = new LoginHistory();
        history.setId(100L);
        history.setEmail("tracker@example.com");
        history.setStatus("SUCCESS");
        history.setLoginTime(LocalDateTime.now());

        when(userRepository.existsById(1L)).thenReturn(true);
        when(loginHistoryRepository.findByUserId(1L, pageable)).thenReturn(new PageImpl<>(List.of(history)));

        Page<LoginHistoryResponseDTO> result = loginTrackingService.getLoginHistoryForUser(1L, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("tracker@example.com", result.getContent().get(0).getUserEmail());
    }

    @Test
    @DisplayName("getLoginHistoryForUser: throws ResourceNotFoundException if user does not exist")
    void getLoginHistoryForUser_UserNotFound_ThrowsException() {
        when(userRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> loginTrackingService.getLoginHistoryForUser(999L, PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("getUserSummary: compiles user login summary with failed attempts count")
    void getUserSummary_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(loginHistoryRepository.countByUserIdAndStatus(1L, "FAILED")).thenReturn(3L);

        LoginSummaryDTO summary = loginTrackingService.getUserSummary(1L);

        assertNotNull(summary);
        assertEquals(1L, summary.getUserId());
        assertEquals("tracker@example.com", summary.getEmail());
        assertEquals(10L, summary.getTotalLogins());
        assertEquals(3L, summary.getFailedAttempts());
    }

    @Test
    @DisplayName("getUserSummary: throws ResourceNotFoundException if user not found")
    void getUserSummary_UserNotFound_ThrowsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> loginTrackingService.getUserSummary(999L));
    }
}
