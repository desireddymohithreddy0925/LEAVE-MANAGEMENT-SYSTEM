package com.leave_management_system.leave_management_system.security.idempotency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.entity.IdempotencyKey;
import com.leave_management_system.leave_management_system.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public IdempotencyResult processOrAcquire(String key, String userEmail, String path, String method, String requestHash) {
        Optional<IdempotencyKey> existingOpt = findKey(key, userEmail);

        if (existingOpt.isPresent()) {
            IdempotencyKey existing = existingOpt.get();

            // Check if key has expired (e.g. 24h TTL)
            if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.info("Idempotency key '{}' expired at {}. Removing expired record.", key, existing.getExpiresAt());
                removeKey(key, userEmail);
            } else if ("IN_PROGRESS".equals(existing.getStatus())) {
                log.warn("Concurrent in-progress request detected for idempotency key '{}' and user '{}'", key, userEmail);
                return IdempotencyResult.inProgress();
            } else if ("COMPLETED".equals(existing.getStatus())) {
                if (existing.getRequestHash().equals(requestHash)) {
                    log.info("Replaying cached response for idempotency key '{}' and user '{}'", key, userEmail);
                    Map<String, String> headers = deserializeHeaders(existing.getResponseHeaders());
                    return IdempotencyResult.replay(
                            existing.getResponseStatus() != null ? existing.getResponseStatus() : 200,
                            existing.getResponseBody(),
                            existing.getContentType() != null ? existing.getContentType() : "application/json",
                            headers
                    );
                } else {
                    log.warn("Payload mismatch for idempotency key '{}' and user '{}'", key, userEmail);
                    return IdempotencyResult.payloadMismatch();
                }
            } else if ("FAILED".equals(existing.getStatus())) {
                log.info("Previous execution failed for idempotency key '{}'. Allowing retry.", key);
                removeKey(key, userEmail);
            }
        }

        IdempotencyKey newRecord = new IdempotencyKey(
                key,
                userEmail,
                path,
                method,
                requestHash,
                "IN_PROGRESS",
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(24)
        );

        try {
            insertNewKey(newRecord);
            return IdempotencyResult.newExecution();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent insert race for idempotency key '{}': {}", key, ex.getMessage());
            return IdempotencyResult.inProgress();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyKey> findKey(String key, String userEmail) {
        return idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(key, userEmail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertNewKey(IdempotencyKey record) {
        idempotencyKeyRepository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeKey(String key, String userEmail) {
        idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(key, userEmail)
                .ifPresent(record -> {
                    idempotencyKeyRepository.delete(record);
                    idempotencyKeyRepository.flush();
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeOperation(String key, String userEmail, int statusCode, String responseBody,
                                  String contentType, Map<String, String> headers) {
        idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(key, userEmail).ifPresent(record -> {
            record.setStatus("COMPLETED");
            record.setResponseStatus(statusCode);
            record.setResponseBody(responseBody);
            record.setContentType(contentType);
            record.setResponseHeaders(serializeHeaders(headers));
            idempotencyKeyRepository.saveAndFlush(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failOperation(String key, String userEmail, int statusCode, String responseBody) {
        idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(key, userEmail).ifPresent(record -> {
            record.setStatus("FAILED");
            record.setResponseStatus(statusCode);
            record.setResponseBody(responseBody);
            idempotencyKeyRepository.saveAndFlush(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanupExpiredKeys() {
        int deleted = idempotencyKeyRepository.deleteExpiredKeys(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency records.", deleted);
        }
        return deleted;
    }

    private String serializeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            log.warn("Failed to serialize idempotency response headers: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> deserializeHeaders(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize idempotency response headers: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
