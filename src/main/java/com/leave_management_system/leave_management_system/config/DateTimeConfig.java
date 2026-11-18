package com.leave_management_system.leave_management_system.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Enterprise Date/Time Configuration providing a centralized {@link Clock} bean.
 * Allows controlled, deterministic time simulation in test suites and standard
 * system clock execution in production.
 */
@Configuration
public class DateTimeConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
