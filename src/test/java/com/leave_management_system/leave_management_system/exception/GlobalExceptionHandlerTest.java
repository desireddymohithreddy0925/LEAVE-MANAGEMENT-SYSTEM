package com.leave_management_system.leave_management_system.exception;

import com.leave_management_system.leave_management_system.dto.ErrorResponseDTO;
import com.leave_management_system.leave_management_system.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GlobalExceptionHandlerTest {

    @Mock
    private AuditService auditService;

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler(auditService);
    }

    @Test
    @DisplayName("handleResourceNotFound: returns 404 Not Found")
    void handleResourceNotFound_Returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Department not found with id: 1");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleResourceNotFound(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Department not found with id: 1", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleDuplicateResourceException: returns 409 Conflict")
    void handleDuplicateResource_Returns409() {
        DuplicateResourceException ex = new DuplicateResourceException("Department name already exists");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleDuplicateResourceException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("Department name already exists", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleInsufficientLeaveException: returns 400 Bad Request")
    void handleInsufficientLeave_Returns400() {
        InsufficientLeaveException ex = new InsufficientLeaveException("Requested 5 days but only 2 available");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleInsufficientLeaveException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Requested 5 days but only 2 available", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleIllegalArgumentException: returns 400 Bad Request")
    void handleIllegalArgument_Returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid date parameter");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleIllegalArgumentException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Invalid date parameter", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleIllegalStateException: returns 409 Conflict")
    void handleIllegalState_Returns409() {
        IllegalStateException ex = new IllegalStateException("Department still has assigned employees");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleIllegalStateException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("Department still has assigned employees", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleBadCredentialsException: returns 401 Unauthorized")
    void handleBadCredentials_Returns401() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleBadCredentialsException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Invalid email or password", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleLockedException: returns 401 Unauthorized with Account Locked message")
    void handleLockedException_Returns401() {
        LockedException ex = new LockedException("User account is locked");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleLockedException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("User account is locked"));
    }

    @Test
    @DisplayName("handleConcurrencyLockingException: returns 409 Conflict")
    void handleConcurrencyLockingException_Returns409() {
        org.springframework.dao.PessimisticLockingFailureException ex =
                new org.springframework.dao.PessimisticLockingFailureException("Could not obtain lock");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleConcurrencyLockingException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("updated concurrently"));
    }

    @Test
    @DisplayName("handleAccessDeniedException: returns 403 Forbidden and audits security event")
    void handleAccessDenied_Returns403() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden action");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleAccessDeniedException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().getStatus());
        verify(auditService, times(1)).logSecurityEvent(any(), isNull(), isNull(), contains("Forbidden action"));
    }

    @Test
    @DisplayName("handleTokenRefreshException: returns 401 Unauthorized and audits security event")
    void handleTokenRefreshException_Returns401() {
        TokenRefreshException ex = new TokenRefreshException("Refresh token was expired");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleTokenRefreshException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().getStatus());
        verify(auditService, times(1)).logSecurityEvent(any(), isNull(), isNull(), eq("Refresh token was expired"));
    }

    @Test
    @DisplayName("handleGenericException: returns 500 Internal Server Error")
    void handleGenericException_Returns500() {
        RuntimeException ex = new RuntimeException("Unexpected internal failure");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleGenericException(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
    }
}

