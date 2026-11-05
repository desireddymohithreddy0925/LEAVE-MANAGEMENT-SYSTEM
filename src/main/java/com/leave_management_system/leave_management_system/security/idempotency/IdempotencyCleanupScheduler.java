package com.leave_management_system.leave_management_system.security.idempotency;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    private final IdempotencyService idempotencyService;

    @Value("${idempotency.cleanup-rate-ms:3600000}")
    private long cleanupRateMs = 3600000L;

    @Value("${idempotency.cleanup-initial-delay-ms:60000}")
    private long cleanupInitialDelayMs = 60000L;

    public IdempotencyCleanupScheduler(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @PostConstruct
    public void validateSchedulerConfig() {
        if (cleanupRateMs < 1000) {
            throw new IllegalArgumentException("Invalid idempotency configuration: cleanup-rate-ms must be at least 1,000 ms (current: " + cleanupRateMs + ")");
        }
        if (cleanupInitialDelayMs < 0) {
            throw new IllegalArgumentException("Invalid idempotency configuration: cleanup-initial-delay-ms cannot be negative (current: " + cleanupInitialDelayMs + ")");
        }
    }

    /**
     * Periodically cleans up expired idempotency records.
     * Defaults to running once every hour (3600000 ms).
     */
    @Scheduled(fixedRateString = "${idempotency.cleanup-rate-ms:3600000}", initialDelayString = "${idempotency.cleanup-initial-delay-ms:60000}")
    public void scheduleCleanup() {
        try {
            int deleted = idempotencyService.cleanupExpiredKeys();
            if (deleted > 0) {
                log.info("Scheduled idempotency cleanup: removed {} expired records", deleted);
            }
        } catch (Exception e) {
            log.error("Error during scheduled idempotency key cleanup: {}", e.getMessage(), e);
        }
    }
}

