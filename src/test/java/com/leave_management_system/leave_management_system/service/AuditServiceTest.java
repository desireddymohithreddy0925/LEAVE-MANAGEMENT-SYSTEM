package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.AuditLogResponseDTO;
import com.leave_management_system.leave_management_system.entity.AuditLog;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.AuditLogRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuditService auditService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(10L);
        testUser.setEmail("tester@test.com");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logAction_Success() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditService.logAction("LEAVE_APPLIED", "LeaveRequest", 100L, null, "{\"reason\":\"Vacation\"}");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("LEAVE_APPLIED", saved.getAction());
        assertEquals("LeaveRequest", saved.getEntityType());
        assertEquals(100L, saved.getEntityId());
        assertNull(saved.getOldValue());
        assertNotNull(saved.getNewValue());
        assertNotNull(saved.getTimestamp());
    }

    @Test
    void logAction_WithNullValues_Succeeds() {
        auditService.logAction("TEST_ACTION", "TestEntity", null, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("TEST_ACTION", saved.getAction());
        assertNull(saved.getOldValue());
        assertNull(saved.getNewValue());
    }

    @Test
    void logAction_MasksSensitiveFields_PasswordsAndTokens() {
        String sensitiveJson = "{\"username\":\"admin\",\"password\":\"superSecret123\",\"token\":\"jwt.token.val\"}";
        auditService.logAction("USER_UPDATE", "User", 1L, null, sensitiveJson);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertFalse(saved.getNewValue().contains("superSecret123"));
        assertFalse(saved.getNewValue().contains("jwt.token.val"));
        assertTrue(saved.getNewValue().contains("[REDACTED]"));
    }

    @Test
    void logAction_ExtractsUserFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester@test.com", "password", java.util.Collections.emptyList())
        );
        when(userRepository.findByEmail("tester@test.com")).thenReturn(Optional.of(testUser));

        auditService.logAction("LEAVE_APPROVED", "LeaveRequest", 101L, null, "Approved");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertNotNull(saved.getUser());
        assertEquals(10L, saved.getUser().getId());
    }

    @Test
    void logAction_AnonymousUser_DoesNotSetUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null)
        );

        auditService.logAction("PUBLIC_ACTION", "Public", null, null, "OK");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertNull(saved.getUser());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void logAction_LongValues_TruncatesTo1000Chars() {
        String longString = "A".repeat(1500);
        auditService.logAction("LONG_ACTION", "Test", 1L, longString, longString);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(1000, saved.getOldValue().length());
        assertTrue(saved.getOldValue().endsWith("..."));
        assertEquals(1000, saved.getNewValue().length());
        assertTrue(saved.getNewValue().endsWith("..."));
    }

    @Test
    void logSecurityEvent_Success() {
        when(userRepository.findByEmail("tester@test.com")).thenReturn(Optional.of(testUser));

        auditService.logSecurityEvent("SUCCESSFUL_LOGIN", "192.168.1.100", "tester@test.com", "Logged in via Web");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("SUCCESSFUL_LOGIN", saved.getAction());
        assertEquals("SECURITY_EVENT", saved.getEntityType());
        assertEquals("192.168.1.100", saved.getIpAddress());
        assertNotNull(saved.getUser());
        assertEquals("Logged in via Web", saved.getNewValue());
    }

    @Test
    void logSecurityEvent_AutoExtractsUserFromContext_WhenEmailNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester@test.com", "password", java.util.Collections.emptyList())
        );
        when(userRepository.findByEmail("tester@test.com")).thenReturn(Optional.of(testUser));

        auditService.logSecurityEvent("USER_LOGOUT", "127.0.0.1", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("USER_LOGOUT", saved.getAction());
        assertNotNull(saved.getUser());
        assertEquals("tester@test.com", saved.getUser().getEmail());
    }

    @Test
    void searchAuditLogs_WithAllParameters_CallsRepository() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setAction("LEAVE_APPLIED");
        log.setEntityType("LeaveRequest");
        log.setEntityId(200L);
        log.setTimestamp(LocalDateTime.now());

        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        LocalDateTime now = LocalDateTime.now();
        Page<AuditLogResponseDTO> result = auditService.searchAuditLogs(
                10L, "LEAVE_APPLIED", "LeaveRequest", 200L,
                now.minusDays(1), now.plusDays(1), PageRequest.of(0, 10)
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("LEAVE_APPLIED", result.getContent().get(0).getAction());
    }

    @Test
    void getAuditLogById_Found() {
        AuditLog log = new AuditLog();
        log.setId(55L);
        log.setAction("EMPLOYEE_CREATED");
        log.setEntityType("Employee");
        log.setTimestamp(LocalDateTime.now());
        when(auditLogRepository.findById(55L)).thenReturn(Optional.of(log));

        AuditLogResponseDTO result = auditService.getAuditLogById(55L);

        assertNotNull(result);
        assertEquals(55L, result.getId());
        assertEquals("EMPLOYEE_CREATED", result.getAction());
    }

    @Test
    void getAuditLogById_NotFound_ThrowsException() {
        when(auditLogRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> auditService.getAuditLogById(999L));
    }

    @Test
    void getAuditLogsByEntity_CallsSearch() {
        Page<AuditLog> emptyPage = new PageImpl<>(List.of());
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        Page<AuditLogResponseDTO> result = auditService.getAuditLogsByEntity("LeaveRequest", 50L, PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        verify(auditLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
