package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.SecurityEventResponseDTO;
import com.leave_management_system.leave_management_system.dto.SecurityEventSummaryDTO;
import com.leave_management_system.leave_management_system.dto.UpdateSecurityEventStatusDTO;
import com.leave_management_system.leave_management_system.entity.SecurityEvent;
import com.leave_management_system.leave_management_system.entity.SecurityEventSeverity;
import com.leave_management_system.leave_management_system.entity.SecurityEventStatus;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.SecurityEventRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.SecurityEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityEventManagementServiceTest {

    @Mock
    private SecurityEventRepository securityEventRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SecurityEventManagementService securityEventManagementService;

    private SecurityEvent testEvent;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("admin@example.com");

        testEvent = new SecurityEvent();
        testEvent.setId(10L);
        testEvent.setEventType(SecurityEventType.SUSPICIOUS_TOKEN_ACTIVITY);
        testEvent.setSeverity(SecurityEventSeverity.HIGH);
        testEvent.setStatus(SecurityEventStatus.OPEN);
        testEvent.setThreatScore(80);
        testEvent.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("recordSecurityEvent: assigns default threat score and severity, sanitizes details, and saves")
    void recordSecurityEvent_Success() {
        when(securityEventRepository.save(any(SecurityEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityEvent created = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.PRIVILEGE_ESCALATION,
                "192.168.1.1",
                "attacker@example.com",
                "/api/admin/roles",
                "Attempted admin role assignment with token=\"secretToken123\"",
                "Mozilla/5.0"
        );

        assertNotNull(created);
        assertEquals(SecurityEventType.PRIVILEGE_ESCALATION, created.getEventType());
        assertEquals(SecurityEventSeverity.CRITICAL, created.getSeverity());
        assertEquals(SecurityEventStatus.OPEN, created.getStatus());
        assertTrue(created.getThreatScore() >= 90);
        assertFalse(created.getDetails().contains("secretToken123"), "Sensitive token value must be sanitized");
        verify(securityEventRepository, atLeastOnce()).save(any(SecurityEvent.class));
    }

    @Test
    @DisplayName("getSecurityEventById: returns mapped DTO when event is found")
    void getSecurityEventById_Success() {
        when(securityEventRepository.findById(10L)).thenReturn(Optional.of(testEvent));

        SecurityEventResponseDTO dto = securityEventManagementService.getSecurityEventById(10L);

        assertNotNull(dto);
        assertEquals(10L, dto.getId());
        assertEquals(SecurityEventType.SUSPICIOUS_TOKEN_ACTIVITY, dto.getEventType());
        assertEquals(SecurityEventSeverity.HIGH, dto.getSeverity());
    }

    @Test
    @DisplayName("getSecurityEventById: throws ResourceNotFoundException when event not found")
    void getSecurityEventById_NotFound_ThrowsException() {
        when(securityEventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> securityEventManagementService.getSecurityEventById(999L));
    }

    @Test
    @DisplayName("updateStatus: transitions OPEN to RESOLVED, sets notes, and assigns resolving user")
    void updateStatus_ToResolved_Success() {
        UpdateSecurityEventStatusDTO dto = new UpdateSecurityEventStatusDTO();
        dto.setStatus(SecurityEventStatus.RESOLVED);
        dto.setResolutionNotes("Investigated and mitigated");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin@example.com");

        when(securityEventRepository.findById(10L)).thenReturn(Optional.of(testEvent));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(testUser));
        when(securityEventRepository.save(any(SecurityEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityEventResponseDTO result = securityEventManagementService.updateStatus(10L, dto, auth);

        assertNotNull(result);
        assertEquals(SecurityEventStatus.RESOLVED, result.getStatus());
        assertEquals("Investigated and mitigated", testEvent.getResolutionNotes());
        assertNotNull(testEvent.getResolvedAt());
        assertEquals(testUser, testEvent.getResolvedBy());
        verify(securityEventRepository, times(1)).save(testEvent);
    }

    @Test
    @DisplayName("updateStatus: throws IllegalArgumentException on same status transition")
    void updateStatus_SameStatus_ThrowsException() {
        UpdateSecurityEventStatusDTO dto = new UpdateSecurityEventStatusDTO();
        dto.setStatus(SecurityEventStatus.OPEN);

        when(securityEventRepository.findById(10L)).thenReturn(Optional.of(testEvent));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> securityEventManagementService.updateStatus(10L, dto, null));
        assertTrue(ex.getMessage().contains("already in status"));
        verify(securityEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus: throws IllegalArgumentException when dto or status is null")
    void updateStatus_NullStatus_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> securityEventManagementService.updateStatus(10L, null, null));
    }

    @Test
    @DisplayName("getSummary: returns counts across all statuses and threat levels")
    void getSummary_Success() {
        when(securityEventRepository.count()).thenReturn(25L);
        when(securityEventRepository.countByStatus(SecurityEventStatus.OPEN)).thenReturn(10L);
        when(securityEventRepository.countByStatus(SecurityEventStatus.INVESTIGATING)).thenReturn(5L);
        when(securityEventRepository.countByStatus(SecurityEventStatus.RESOLVED)).thenReturn(10L);

        SecurityEventSummaryDTO summary = securityEventManagementService.getSummary();

        assertNotNull(summary);
        assertEquals(25L, summary.getTotalEvents());
        assertEquals(10L, summary.getOpenCount());
        assertEquals(5L, summary.getInvestigatingCount());
        assertEquals(10L, summary.getResolvedCount());
    }
}
