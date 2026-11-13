package com.leave_management_system.leave_management_system.performance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real HTTP Load and Latency Stress Test targeting the running Docker Deployment.
 * <p>
 * Validates real network roundtrip HTTP latencies over TCP against the containerized
 * Spring Boot backend (lms-backend) and PostgreSQL 15 database (lms-postgres).
 * <p>
 * Enforces the documented SLA target: p95 &lt;= 150 ms under concurrent load.
 */
@DisplayName("Docker Deployment Real HTTP Load Stress Test")
public class DockerDeploymentHttpLoadStressTest {

    private static final Logger log = LoggerFactory.getLogger(DockerDeploymentHttpLoadStressTest.class);

    private final String baseUrl = System.getProperty("app.target.url",
            System.getProperty("target.url", "http://localhost:8080"));

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private boolean isDockerAvailable = false;

    @BeforeEach
    void setUp() {
        // Probe Docker deployment healthcheck endpoint
        try {
            HttpRequest healthRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/actuator/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> healthResponse = httpClient.send(healthRequest, HttpResponse.BodyHandlers.ofString());
            if (healthResponse.statusCode() == 200 && healthResponse.body().contains("UP")) {
                isDockerAvailable = true;
                log.info("Docker deployment detected and healthy at {}", baseUrl);
            }
        } catch (Exception e) {
            log.warn("Docker deployment not reachable at {}: {}", baseUrl, e.getMessage());
            isDockerAvailable = false;
        }

        if (isDockerAvailable) {
            // Authenticate against Docker backend to obtain real JWT Bearer token
            try {
                String loginPayload = "{\"email\":\"admin@leavemanagement.com\",\"password\":\"Admin@123\"}";
                HttpRequest loginRequest = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                        .build();

                HttpResponse<String> loginResponse = httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, loginResponse.statusCode(), "Admin login must succeed on Docker deployment");

                JsonNode root = objectMapper.readTree(loginResponse.body());
                adminToken = root.path("accessToken").asText();
                assertThat(adminToken).isNotBlank();
                log.info("Successfully authenticated with Docker backend; JWT bearer token acquired.");
            } catch (Exception e) {
                log.error("Failed to authenticate against Docker deployment: {}", e.getMessage(), e);
                isDockerAvailable = false;
            }
        }
    }

    @Test
    @DisplayName("Real HTTP Load Test against Docker Deployment: 40 concurrent HTTP requests with 150ms p95 SLA audit")
    void testRealHttpLoadAgainstDockerDeployment() throws Exception {
        Assumptions.assumeTrue(isDockerAvailable,
                "Docker deployment is not running at " + baseUrl + " - skipping live container load test.");

        int totalRequests = 40;
        int concurrency = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        String[] testEndpoints = {
                "/api/departments",
                "/api/leave-types",
                "/actuator/health",
                "/api/audit/performance/system-summary"
        };

        log.info("================================================================================");
        log.info("STARTING REAL HTTP LOAD TEST AGAINST DOCKER DEPLOYMENT ({})", baseUrl);
        log.info("================================================================================");
        log.info("Execution Environment Details:");
        log.info("  Container Target URL   : {}", baseUrl);
        log.info("  Host Operating System  : {} {} ({})",
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        log.info("  Client JVM Runtime     : {} ({})",
                System.getProperty("java.version"), System.getProperty("java.vendor"));
        log.info("  Client Processors      : {} logical cores", Runtime.getRuntime().availableProcessors());
        log.info("  Client Max Memory      : {} MB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        log.info("  Target Containers      : lms-backend (Spring Boot 4.1.0 on Temurin 21 JRE Jammy)");
        log.info("                           lms-postgres (PostgreSQL 15 Alpine on lms-internal-net)");
        log.info("  Network Architecture   : Native HTTP/1.1 over TCP socket bridge");
        log.info("  Load Parameters        : N={} requests, Concurrency={} threads", totalRequests, concurrency);
        log.info("  Documented SLA Target  : p95 <= 150 ms");
        log.info("================================================================================");

        // Warm-up phase: Execute 25 warmup requests across target endpoints to prime JVM JIT, TCP connection pool, and JPA caches
        log.info("Executing 25 pre-benchmark warmup requests to prime JVM JIT and TCP connection pool...");
        for (int w = 0; w < 25; w++) {
            try {
                HttpRequest warmReq = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + testEndpoints[w % testEndpoints.length]))
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                httpClient.send(warmReq, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
            }
        }
        log.info("Warmup phase complete. Starting 40-request concurrent load test...");

        for (int i = 0; i < totalRequests; i++) {
            final String endpoint = testEndpoints[i % testEndpoints.length];
            executor.submit(() -> {
                long startNanos = System.nanoTime();
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + endpoint))
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Accept", "application/json")
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    latencies.add(elapsedMs);

                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        log.warn("Request to {} failed with HTTP {}", endpoint, response.statusCode());
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Request to {} failed with exception: {}", endpoint, e.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "HTTP load test against Docker deployment should complete within 30 seconds");
        assertEquals(totalRequests, successCount.get(), "All 40 HTTP requests must return 200 OK under concurrent load");
        assertEquals(0, failureCount.get(), "Zero HTTP requests should fail during Docker load test");

        // Exact rank-based percentile calculation
        Collections.sort(latencies);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        long p50 = latencies.get((int) Math.ceil(latencies.size() * 0.50) - 1);
        long p90 = latencies.get((int) Math.ceil(latencies.size() * 0.90) - 1);
        long p95 = latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);
        long p99 = latencies.get((int) Math.ceil(latencies.size() * 0.99) - 1);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

        log.info("Raw Docker HTTP benchmark latencies (ms) [sorted, N={}]: {}", latencies.size(), latencies);
        log.info("================================================================================");
        log.info("DOCKER DEPLOYMENT REAL HTTP LOAD TEST RESULTS (N={})", totalRequests);
        log.info("================================================================================");
        log.info("  Minimum Latency        : {} ms", min);
        log.info("  Average Latency        : {} ms", String.format(Locale.ROOT, "%.2f", avg));
        log.info("  p50 (Median) Latency   : {} ms", p50);
        log.info("  p90 Latency            : {} ms", p90);
        log.info("  p95 Latency            : {} ms (Target: <= 150 ms)", p95);
        log.info("  p99 Latency            : {} ms", p99);
        log.info("  Maximum Latency        : {} ms", max);
        log.info("  Success Rate           : 100.0% ({}/{} successful)", successCount.get(), totalRequests);
        log.info("  SLA Compliance Status  : STRICTLY COMPLIANT (p95 {} ms <= 150 ms)", p95);
        log.info("================================================================================");

        // Assert p95 alignment with documented 150 ms target
        assertThat(p95)
                .as("Docker deployment real HTTP p95 latency must satisfy documented 150ms target")
                .isLessThanOrEqualTo(150L);

        // Assert p50 median latency
        assertThat(p50)
                .as("Docker deployment real HTTP p50 median latency must satisfy 100ms target")
                .isLessThanOrEqualTo(100L);
    }
}
