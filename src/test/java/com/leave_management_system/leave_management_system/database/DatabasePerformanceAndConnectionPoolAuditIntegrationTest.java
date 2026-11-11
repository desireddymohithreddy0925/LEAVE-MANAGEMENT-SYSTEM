package com.leave_management_system.leave_management_system.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.ConnectionPoolMetricsDTO;
import com.leave_management_system.leave_management_system.dto.QueryPerformanceDiagnosticsDTO;
import com.leave_management_system.leave_management_system.dto.SystemPerformanceSummaryDTO;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.PermissionRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.service.PerformanceAndDatabaseAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Database Performance & Connection Pool Audit Integration Test Suite")
public class DatabasePerformanceAndConnectionPoolAuditIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private PerformanceAndDatabaseAuditService performanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String runId;
    private User adminUser;
    private String adminToken;
    private User employeeUser;
    private String employeeToken;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        runId = UUID.randomUUID().toString().substring(0, 8);

        // Set up Admin Role with SYSTEM_AUDIT and ADMIN_VIEW
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        Permission permAdminView = permissionRepository.findByName("ADMIN_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("ADMIN_VIEW", "View admin data")));
        Permission permSystemAudit = permissionRepository.findByName("SYSTEM_AUDIT")
                .orElseGet(() -> permissionRepository.save(new Permission("SYSTEM_AUDIT", "Audit system")));
        Permission permLeaveView = permissionRepository.findByName("LEAVE_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW", "View leaves")));

        adminRole.getPermissions().clear();
        adminRole.getPermissions().addAll(Set.of(permAdminView, permSystemAudit, permLeaveView));
        roleRepository.save(adminRole);

        employeeRole.getPermissions().clear();
        employeeRole.getPermissions().addAll(Set.of(permLeaveView));
        roleRepository.save(employeeRole);

        // Admin User
        adminUser = new User();
        adminUser.setEmail("admin-perf-" + runId + "@enterprise.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        // Regular Employee User
        employeeUser = new User();
        employeeUser.setEmail("emp-perf-" + runId + "@enterprise.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.setAccountNonLocked(true);
        employeeUser.getRoles().add(employeeRole);
        employeeUser = userRepository.save(employeeUser);

        adminToken = generateToken(adminUser, adminRole);
        employeeToken = generateToken(employeeUser, employeeRole);
    }

    private String generateToken(User user, Role role) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName().replace("ROLE_", "")));
        if (role.getPermissions() != null) {
            role.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
        }
        UserDetails details = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(), authorities);
        return jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @AfterEach
    public void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Service Layer Performance Diagnostics Verification")
    class ServiceLayerDiagnosticsTests {

        @Test
        @DisplayName("Connection pool metrics report valid pool configuration and healthy status")
        void testConnectionPoolMetricsReport() {
            ConnectionPoolMetricsDTO metrics = performanceService.getConnectionPoolMetrics();

            assertThat(metrics).isNotNull();
            assertThat(metrics.getPoolName()).isNotEmpty();
            assertThat(metrics.getActiveConnections()).isGreaterThanOrEqualTo(0);
            assertThat(metrics.getIdleConnections()).isGreaterThanOrEqualTo(0);
            assertThat(metrics.getMaximumPoolSize()).isGreaterThan(0);
            assertThat(metrics.getThreadsAwaitingConnection()).isEqualTo(0);
            assertThat(metrics.getPoolStatus()).isIn("HEALTHY", "WARNING");
        }

        @Test
        @DisplayName("Query performance diagnostics verify indexed tables and N+1 query protections")
        void testQueryPerformanceDiagnostics() {
            QueryPerformanceDiagnosticsDTO diagnostics = performanceService.getQueryPerformanceDiagnostics();

            assertThat(diagnostics).isNotNull();
            assertThat(diagnostics.getStatus()).isEqualTo("OPTIMAL");
            assertThat(diagnostics.getMonitoredTablesCount()).isEqualTo(6);
            assertThat(diagnostics.getTotalIndexesChecked()).isGreaterThanOrEqualTo(0);
            assertThat(diagnostics.getNPlusOneProtections()).isNotEmpty();
            assertThat(diagnostics.getRecommendations()).isNotEmpty();
        }

        @Test
        @DisplayName("System performance summary reports JVM heap and SLA latency thresholds")
        void testSystemPerformanceSummary() {
            SystemPerformanceSummaryDTO summary = performanceService.getSystemPerformanceSummary();

            assertThat(summary).isNotNull();
            assertThat(summary.getAvailableProcessors()).isGreaterThan(0);
            assertThat(summary.getHeapUsedMb()).isGreaterThan(0.0);
            assertThat(summary.getHeapMaxMb()).isGreaterThan(0.0);
            assertThat(summary.getReadSlaTargetMs()).isEqualTo(150L);
            assertThat(summary.getWriteSlaTargetMs()).isEqualTo(250L);
            assertThat(summary.getStatus()).isIn("OPTIMAL", "DEGRADED");
        }

        @Test
        @DisplayName("Concurrent connection acquisition stress executes cleanly without pool exhaustion")
        void testConcurrentConnectionPoolStability() throws InterruptedException {
            int threadCount = 15;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                        if (result != null && result == 1) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Log or handle
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All concurrent database queries should complete within timeout");
            assertEquals(threadCount, successCount.get(), "All concurrent connections must execute successfully");

            // Verify pool is still healthy after concurrent burst
            ConnectionPoolMetricsDTO postMetrics = performanceService.getConnectionPoolMetrics();
            assertThat(postMetrics.getPoolStatus()).isIn("HEALTHY", "WARNING");
            assertThat(postMetrics.getThreadsAwaitingConnection()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("REST API Performance & Pool Audit Endpoints Verification")
    class RestApiPerformanceAuditTests {

        @Test
        @DisplayName("GET /api/audit/performance/pool-status - 401 when unauthorized")
        void testGetPoolStatus_Unauthorized() throws Exception {
            mockMvc.perform(get("/api/audit/performance/pool-status")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/audit/performance/pool-status - 403 when forbidden for standard employee")
        void testGetPoolStatus_Forbidden() throws Exception {
            mockMvc.perform(get("/api/audit/performance/pool-status")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/audit/performance/pool-status - 200 OK for Admin")
        void testGetPoolStatus_Success() throws Exception {
            mockMvc.perform(get("/api/audit/performance/pool-status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.poolName").exists())
                    .andExpect(jsonPath("$.activeConnections").isNumber())
                    .andExpect(jsonPath("$.idleConnections").isNumber())
                    .andExpect(jsonPath("$.maximumPoolSize").isNumber())
                    .andExpect(jsonPath("$.threadsAwaitingConnection").value(0))
                    .andExpect(jsonPath("$.poolStatus").value(org.hamcrest.Matchers.isIn(List.of("HEALTHY", "WARNING"))));
        }

        @Test
        @DisplayName("GET /api/audit/performance/query-diagnostics - 200 OK for Admin")
        void testGetQueryDiagnostics_Success() throws Exception {
            mockMvc.perform(get("/api/audit/performance/query-diagnostics")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPTIMAL"))
                    .andExpect(jsonPath("$.monitoredTablesCount").value(6))
                    .andExpect(jsonPath("$.nPlusOneProtections").isArray())
                    .andExpect(jsonPath("$.recommendations").isArray());
        }

        @Test
        @DisplayName("GET /api/audit/performance/system-summary - 200 OK for Admin")
        void testGetSystemSummary_Success() throws Exception {
            mockMvc.perform(get("/api/audit/performance/system-summary")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableProcessors", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.heapUsedMb").isNumber())
                    .andExpect(jsonPath("$.readSlaTargetMs").value(150))
                    .andExpect(jsonPath("$.writeSlaTargetMs").value(250))
                    .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.isIn(List.of("OPTIMAL", "DEGRADED"))));
        }
    }
}

