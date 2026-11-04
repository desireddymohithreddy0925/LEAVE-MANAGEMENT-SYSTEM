package com.leave_management_system.leave_management_system.config;

import com.leave_management_system.leave_management_system.config.properties.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logs a structured, non-sensitive summary of active environment configuration
 * when the application has fully initialized and transitioned to accepting traffic.
 */
@Component
public class EnvironmentConfigLogger {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentConfigLogger.class);
    private static final Pattern DB_HOST_PATTERN = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)");

    private final Environment environment;
    private final AppProperties appProperties;

    public EnvironmentConfigLogger(Environment environment, AppProperties appProperties) {
        this.environment = environment;
        this.appProperties = appProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profilesStr = activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "default (dev)";
        String serverPort = environment.getProperty("server.port", "8080");
        String rawDbUrl = environment.getProperty("spring.datasource.url", "in-memory / none");
        String sanitizedDbTarget = sanitizeJdbcUrl(rawDbUrl);
        String actuatorExposure = environment.getProperty("management.endpoints.web.exposure.include", "none");
        String healthDetails = environment.getProperty("management.endpoint.health.show-details", "never");

        log.info("========================================================================");
        log.info("       Leave Management System - Environment Configuration Loaded       ");
        log.info("========================================================================");
        log.info(" Active Profile(s)      : [{}]", profilesStr);
        log.info(" Server Port            : {}", serverPort);
        log.info(" Database Target        : {}", sanitizedDbTarget);
        log.info(" Liquibase Migrations   : {}", environment.getProperty("spring.liquibase.enabled", "true"));
        log.info(" JPA DDL Validation     : {}", environment.getProperty("spring.jpa.hibernate.ddl-auto", "none"));
        log.info(" JWT Expiration         : {} ms (Access), {} ms (Refresh)",
                appProperties.getJwt().getExpirationMs(),
                appProperties.getJwt().getRefreshExpirationMs());
        log.info(" Account Lockout Policy : Max {} failed attempts -> {} min lockout",
                appProperties.getSecurity().getLockout().getMaxFailedAttempts(),
                appProperties.getSecurity().getLockout().getDurationMinutes());
        log.info(" Actuator Exposure      : Endpoints: [{}], Health Details: [{}]",
                actuatorExposure, healthDetails);
        log.info("========================================================================");

        if (Arrays.asList(activeProfiles).contains("prod")) {
            log.info(" Production profile active. Zero-Trust credential & encryption policies enforced.");
        }
    }

    private String sanitizeJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return "not-configured";
        }
        if (url.startsWith("jdbc:h2:")) {
            return "H2 In-Memory Database";
        }
        Matcher matcher = DB_HOST_PATTERN.matcher(url);
        if (matcher.find()) {
            String host = matcher.group(1);
            String port = matcher.group(2) != null ? matcher.group(2) : "5432";
            String db = matcher.group(3);
            return String.format("PostgreSQL at %s:%s (Database: %s)", host, port, db);
        }
        return "PostgreSQL (external)";
    }
}

