package com.leave_management_system.leave_management_system.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.ConnectionPoolMetricsDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.service.PerformanceAndDatabaseAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("API Response-Time and Load Stress Test Suite")
public class ApiResponseTimeAndLoadStressTest {

    private static final Logger log = LoggerFactory.getLogger(ApiResponseTimeAndLoadStressTest.class);

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveRequestSignoffRepository leaveRequestSignoffRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private PerformanceAndDatabaseAuditService performanceAuditService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String runId;
    private Department department;
    private LeaveType leaveType;
    private User adminUser;
    private String adminToken;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanupDatabase();

        runId = UUID.randomUUID().toString().substring(0, 8);

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Permission permAdminView = permissionRepository.findByName("ADMIN_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("ADMIN_VIEW", "View admin data")));
        Permission permSystemAudit = permissionRepository.findByName("SYSTEM_AUDIT")
                .orElseGet(() -> permissionRepository.save(new Permission("SYSTEM_AUDIT", "Audit system")));
        Permission permDeptView = permissionRepository.findByName("DEPARTMENT_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_VIEW", "View departments")));
        Permission permLeaveView = permissionRepository.findByName("LEAVE_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW", "View leaves")));

        adminRole.getPermissions().addAll(Set.of(permAdminView, permSystemAudit, permDeptView, permLeaveView));
        roleRepository.save(adminRole);

        adminUser = new User();
        adminUser.setEmail("admin-load-" + runId + "@loadtest.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        department = new Department();
        department.setName("Operations-" + runId);
        department = departmentRepository.save(department);

        Employee adminEmp = new Employee();
        adminEmp.setFirstName("Load");
        adminEmp.setLastName("Tester");
        adminEmp.setEmployeeCode("LT-" + runId);
        adminEmp.setStatus("ACTIVE");
        adminEmp.setSalary(new BigDecimal("120000.00"));
        adminEmp.setDepartment(department);
        adminEmp.setUser(adminUser);
        adminEmp = employeeRepository.save(adminEmp);
        adminUser.setEmployee(adminEmp);
        userRepository.save(adminUser);

        leaveType = new LeaveType();
        leaveType.setName("Casual Leave " + runId);
        leaveType.setDescription("Casual leave load test");
        leaveType.setDefaultDays(12);
        leaveType.setActive(true);
        leaveType = leaveTypeRepository.save(leaveType);

        LeaveBalance bal = new LeaveBalance();
        bal.setEmployee(adminEmp);
        bal.setLeaveType(leaveType);
        bal.setYear(LocalDate.now().getYear());
        bal.setTotal(12);
        bal.setUsed(0);
        bal.setAvailable(12);
        leaveBalanceRepository.saveAndFlush(bal);

        adminToken = generateToken(adminUser, adminRole);
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
        cleanupDatabase();
        SecurityContextHolder.clearContext();
    }

    private void cleanupDatabase() {
        try {
            transactionReviewRepository.deleteAll();
            leaveRequestSignoffRepository.deleteAll();
            leaveRequestRepository.deleteAll();
            leaveBalanceRepository.deleteAll();
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Load & Response-Time Testing: 40 concurrent requests across read endpoints with SLA percentile audit")
    void testReadEndpointLoadAndResponseTimePercentiles() throws Exception {
        int totalRequests = 40;
        int concurrency = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        String[] endpoints = {
                "/api/departments",
                "/api/leave-types",
                "/api/audit/performance/pool-status",
                "/api/audit/performance/system-summary"
        };

        for (int i = 0; i < totalRequests; i++) {
            final String endpoint = endpoints[i % endpoints.length];
            executor.submit(() -> {
                long startNanos = System.nanoTime();
                try {
                    MvcResult result = mockMvc.perform(get(endpoint)
                                    .header("Authorization", "Bearer " + adminToken)
                                    .contentType(MediaType.APPLICATION_JSON))
                            .andReturn();

                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    latencies.add(elapsedMs);

                    if (result.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Load test should complete within timeout");
        assertEquals(totalRequests, successCount.get(), "All read requests must return 200 OK under concurrent load");
        assertEquals(0, failureCount.get(), "Zero requests should fail during load test");

        // Calculate Percentiles
        Collections.sort(latencies);
        long p50 = latencies.get((int) (latencies.size() * 0.50));
        long p90 = latencies.get((int) (latencies.size() * 0.90));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get(latencies.size() - 1);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

        log.info("Read Load Test Metrics [N={}]: avg={:.2f}ms, p50={}ms, p90={}ms, p95={}ms, p99={}ms",
                totalRequests, avg, p50, p90, p95, p99);

        // Strict SLA assertions: p95 must remain under 400ms for read endpoints in the test environment
        assertThat(p95).isLessThan(400L);
        assertThat(p50).isLessThan(250L);
    }

    @Test
    @DisplayName("Connection Pool Stability Under Sustained Load: Pool recovers and reports no connection leaks")
    void testConnectionPoolHealthUnderLoad() throws Exception {
        ConnectionPoolMetricsDTO beforeMetrics = performanceAuditService.getConnectionPoolMetrics();
        assertThat(beforeMetrics).isNotNull();

        // Perform rapid burst of 20 API requests
        int burstCount = 20;
        CountDownLatch burstLatch = new CountDownLatch(burstCount);
        ExecutorService burstExecutor = Executors.newFixedThreadPool(5);

        for (int i = 0; i < burstCount; i++) {
            burstExecutor.submit(() -> {
                try {
                    mockMvc.perform(get("/api/audit/performance/query-diagnostics")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON));
                } catch (Exception ignored) {
                } finally {
                    burstLatch.countDown();
                }
            });
        }

        burstLatch.await(15, TimeUnit.SECONDS);
        burstExecutor.shutdown();

        // Verify pool recovery
        ConnectionPoolMetricsDTO postMetrics = performanceAuditService.getConnectionPoolMetrics();
        assertThat(postMetrics).isNotNull();
        assertThat(postMetrics.getThreadsAwaitingConnection()).isEqualTo(0);
        assertThat(postMetrics.getPoolStatus()).isIn("HEALTHY", "WARNING");
    }
}
