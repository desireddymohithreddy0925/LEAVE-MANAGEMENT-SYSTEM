package com.leave_management_system.leave_management_system.security.idempotency;

import com.leave_management_system.leave_management_system.entity.IdempotencyKey;
import com.leave_management_system.leave_management_system.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private static final String KEY = "test-idem-key-123";
    private static final String EMAIL = "user@example.com";
    private static final String PATH = "/api/leave-requests";
    private static final String METHOD = "POST";
    private static final String HASH = "a".repeat(64);

    @Test
    @DisplayName("processOrAcquire: new key acquires reservation and returns newExecution")
    void processOrAcquire_NewKey_AcquiresReservation() {
        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(KEY, EMAIL)).thenReturn(Optional.empty());

        IdempotencyResult result = idempotencyService.processOrAcquire(KEY, EMAIL, PATH, METHOD, HASH);

        assertNotNull(result);
        assertTrue(result.isNewExecution());
        verify(idempotencyKeyRepository, times(1)).saveAndFlush(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("processOrAcquire: returns inProgress when active operation with key is already executing")
    void processOrAcquire_InProgress_ReturnsInProgress() {
        IdempotencyKey existing = new IdempotencyKey(KEY, EMAIL, PATH, METHOD, HASH, "IN_PROGRESS",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(KEY, EMAIL)).thenReturn(Optional.of(existing));

        IdempotencyResult result = idempotencyService.processOrAcquire(KEY, EMAIL, PATH, METHOD, HASH);

        assertNotNull(result);
        assertTrue(result.isInProgress());
        verify(idempotencyKeyRepository, never()).delete(any());
    }

    @Test
    @DisplayName("processOrAcquire: returns replay with cached payload when key is COMPLETED and hash matches")
    void processOrAcquire_CompletedMatchingHash_ReturnsReplay() {
        IdempotencyKey existing = new IdempotencyKey(KEY, EMAIL, PATH, METHOD, HASH, "COMPLETED",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(23));
        existing.setResponseStatus(201);
        existing.setResponseBody("{\"id\":10}");
        existing.setContentType("application/json");
        existing.setResponseHeaders("{\"Location\":\"/api/leave-requests/10\"}");

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(KEY, EMAIL)).thenReturn(Optional.of(existing));

        IdempotencyResult result = idempotencyService.processOrAcquire(KEY, EMAIL, PATH, METHOD, HASH);

        assertNotNull(result);
        assertTrue(result.isReplay());
        assertEquals(201, result.getResponseStatus());
        assertEquals("{\"id\":10}", result.getResponseBody());
        assertEquals("application/json", result.getContentType());
        assertEquals("/api/leave-requests/10", result.getResponseHeaders().get("Location"));
    }

    @Test
    @DisplayName("processOrAcquire: returns payloadMismatch when key is COMPLETED but hash differs")
    void processOrAcquire_CompletedDifferingHash_ReturnsPayloadMismatch() {
        IdempotencyKey existing = new IdempotencyKey(KEY, EMAIL, PATH, METHOD, "different-hash", "COMPLETED",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(23));

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(KEY, EMAIL)).thenReturn(Optional.of(existing));

        IdempotencyResult result = idempotencyService.processOrAcquire(KEY, EMAIL, PATH, METHOD, HASH);

        assertNotNull(result);
        assertTrue(result.isPayloadMismatch());
    }

    @Test
    @DisplayName("processOrAcquire: removes FAILED key and allows retry with newExecution")
    void processOrAcquire_FailedKey_AllowsRetry() {
        IdempotencyKey existing = new IdempotencyKey(KEY, EMAIL, PATH, METHOD, HASH, "FAILED",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(23));

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(KEY, EMAIL)).thenReturn(Optional.of(existing));

        IdempotencyResult result = idempotencyService.processOrAcquire(KEY, EMAIL, PATH, METHOD, HASH);

        assertNotNull(result);
        assertTrue(result.isNewExecution());
        verify(idempotencyKeyRepository, times(1)).delete(existing);
        verify(idempotencyKeyRepository, times(1)).saveAndFlush(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("completeOperation: transitions key to COMPLETED and records response")
    void completeOperation_Success() {
        IdempotencyKey record = new IdempotencyKey();
        record.setStatus("IN_PROGRESS");

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(KEY, EMAIL)).thenReturn(Optional.of(record));

        idempotencyService.completeOperation(KEY, EMAIL, 200, "{\"success\":true}", "application/json", Map.of("X-Custom", "Val"));

        assertEquals("COMPLETED", record.getStatus());
        assertEquals(200, record.getResponseStatus());
        assertEquals("{\"success\":true}", record.getResponseBody());
        assertEquals("application/json", record.getContentType());
        assertTrue(record.getResponseHeaders().contains("X-Custom"));
        verify(idempotencyKeyRepository, times(1)).saveAndFlush(record);
    }

    @Test
    @DisplayName("failOperation: transitions key to FAILED and records error message")
    void failOperation_Success() {
        IdempotencyKey record = new IdempotencyKey();
        record.setStatus("IN_PROGRESS");

        when(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(KEY, EMAIL)).thenReturn(Optional.of(record));

        idempotencyService.failOperation(KEY, EMAIL, 500, "Connection timeout");

        assertEquals("FAILED", record.getStatus());
        assertEquals(500, record.getResponseStatus());
        assertEquals("Connection timeout", record.getResponseBody());
        verify(idempotencyKeyRepository, times(1)).saveAndFlush(record);
    }

    @Test
    @DisplayName("cleanupExpiredKeys: invokes repository deleteExpiredKeys and returns count")
    void cleanupExpiredKeys_Success() {
        when(idempotencyKeyRepository.deleteExpiredKeys(any(LocalDateTime.class))).thenReturn(5);

        int cleaned = idempotencyService.cleanupExpiredKeys();

        assertEquals(5, cleaned);
        verify(idempotencyKeyRepository, times(1)).deleteExpiredKeys(any(LocalDateTime.class));
    }
}
