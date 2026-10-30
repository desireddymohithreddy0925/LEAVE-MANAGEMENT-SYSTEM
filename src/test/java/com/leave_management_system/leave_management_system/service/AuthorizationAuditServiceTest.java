package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.AuthorizationAuditResponseDTO;
import com.leave_management_system.leave_management_system.dto.AuthorizationSummaryDTO;
import com.leave_management_system.leave_management_system.entity.AuthorizationAuditLog;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.AuthorizationAuditLogRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthorizationAuditServiceTest {

    @Mock
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthorizationAuditService authorizationAuditService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("authz@example.com");
    }

    @Test
    @DisplayName("recordGranted: saves authorization log with GRANTED decision")
    void recordGranted_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(authorizationAuditLogRepository.save(any(AuthorizationAuditLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AuthorizationAuditLog log = authorizationAuditService.recordGranted(
                "authz@example.com", 1L, "LEAVE_APPROVE", "LeaveRequest#10", "LEAVE_APPROVE", "Manager approved"
        );

        assertNotNull(log);
        assertEquals("authz@example.com", log.getEmail());
        assertEquals("LEAVE_APPROVE", log.getAction());
        assertEquals("LeaveRequest#10", log.getResource());
        assertEquals("GRANTED", log.getDecision());
        assertEquals(testUser, log.getUser());
        verify(authorizationAuditLogRepository, times(1)).save(any(AuthorizationAuditLog.class));
    }

    @Test
    @DisplayName("recordDenied: saves authorization log with DENIED decision")
    void recordDenied_Success() {
        when(userRepository.findByEmail("authz@example.com")).thenReturn(Optional.of(testUser));
        when(authorizationAuditLogRepository.save(any(AuthorizationAuditLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AuthorizationAuditLog log = authorizationAuditService.recordDenied(
                "authz@example.com", null, "SALARY_VIEW", "Employee#5", "SALARY_VIEW", "Access denied"
        );

        assertNotNull(log);
        assertEquals("DENIED", log.getDecision());
        assertEquals("SALARY_VIEW", log.getAction());
        verify(authorizationAuditLogRepository, times(1)).save(any(AuthorizationAuditLog.class));
    }

    @Test
    @DisplayName("recordDecision: truncates overly long action, resource, permission, and reason fields safely")
    void recordDecision_TruncatesLongFields() {
        String longAction = "A".repeat(150);
        String longResource = "R".repeat(300);
        String longPermission = "P".repeat(200);
        String longReason = "Re".repeat(300);

        ArgumentCaptor<AuthorizationAuditLog> captor = ArgumentCaptor.forClass(AuthorizationAuditLog.class);
        when(authorizationAuditLogRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        authorizationAuditService.recordDecision(
                "test@example.com", null, longAction, longResource, longPermission, "DENIED", longReason, "10.0.0.1"
        );

        AuthorizationAuditLog captured = captor.getValue();
        assertNotNull(captured);
        assertTrue(captured.getAction().length() <= 100);
        assertTrue(captured.getResource().length() <= 255);
        assertTrue(captured.getPermissionRequired().length() <= 150);
        assertTrue(captured.getReason().length() <= 500);
    }

    @Test
    @DisplayName("recordDecision: defaults to anonymousUser when email is missing or empty")
    void recordDecision_EmptyEmail_DefaultsToAnonymous() {
        ArgumentCaptor<AuthorizationAuditLog> captor = ArgumentCaptor.forClass(AuthorizationAuditLog.class);
        when(authorizationAuditLogRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        authorizationAuditService.recordDecision(
                "   ", null, "CHECK", "Resource", "PERM", "DENIED", "Reason", "127.0.0.1"
        );

        AuthorizationAuditLog captured = captor.getValue();
        assertEquals("anonymousUser", captured.getEmail());
    }

    @Test
    @DisplayName("getSummaryMetrics: computes total, granted, denied, and denial percentage")
    void getSummaryMetrics_Success() {
        when(authorizationAuditLogRepository.count()).thenReturn(100L);
        when(authorizationAuditLogRepository.countByDecision("GRANTED")).thenReturn(80L);
        when(authorizationAuditLogRepository.countByDecision("DENIED")).thenReturn(20L);
        when(authorizationAuditLogRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(new AuthorizationAuditLog())));

        AuthorizationSummaryDTO summary = authorizationAuditService.getSummaryMetrics();

        assertNotNull(summary);
        assertEquals(100L, summary.getTotalDecisions());
        assertEquals(80L, summary.getGrantedCount());
        assertEquals(20L, summary.getDeniedCount());
        assertEquals(20.0, summary.getDenialRatePercentage());
    }

    @Test
    @DisplayName("getAuditsForCurrentUser: returns page of audits for given user details")
    void getAuditsForCurrentUser_Success() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("current@example.com");

        AuthorizationAuditLog log = new AuthorizationAuditLog();
        log.setEmail("current@example.com");
        log.setAction("ACTION");
        log.setDecision("GRANTED");
        log.setTimestamp(LocalDateTime.now());

        Pageable pageable = PageRequest.of(0, 10);
        when(authorizationAuditLogRepository.findByEmailOrderByTimestampDesc("current@example.com", pageable))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuthorizationAuditResponseDTO> result = authorizationAuditService.getAuditsForCurrentUser(userDetails, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("current@example.com", result.getContent().get(0).getEmail());
    }
}
