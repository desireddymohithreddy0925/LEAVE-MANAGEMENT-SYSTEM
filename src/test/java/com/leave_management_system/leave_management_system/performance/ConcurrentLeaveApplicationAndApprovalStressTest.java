package com.leave_management_system.leave_management_system.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Concurrent Leave Application and Approval Stress Test Suite")
public class ConcurrentLeaveApplicationAndApprovalStressTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentLeaveApplicationAndApprovalStressTest.class);

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
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String runId;
    private Department department;
    private LeaveType annualLeaveType;
    private User adminUser;
    private Employee adminEmployee;
    private String adminToken;

    private User managerUser;
    private Employee managerEmployee;
    private String managerToken;

    private User hrUser;
    private Employee hrEmployee;
    private String hrToken;

    private User employeeUser;
    private Employee regularEmployee;
    private String employeeToken;
    private LeaveBalance employeeBalance;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanupDatabase();

        runId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Roles & Permissions Setup
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        Role hrRole = roleRepository.findByName("HR").orElseGet(() -> roleRepository.save(new Role("HR")));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        Permission permAdminView = permissionRepository.findByName("ADMIN_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("ADMIN_VIEW", "View admin data")));
        Permission permLeaveApprove = permissionRepository.findByName("LEAVE_APPROVE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_APPROVE", "Approve leaves")));
        Permission permLeaveCreate = permissionRepository.findByName("LEAVE_CREATE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CREATE", "Create leave requests")));
        Permission permLeaveApply = permissionRepository.findByName("LEAVE_APPLY")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_APPLY", "Apply for leaves")));
        Permission permLeaveView = permissionRepository.findByName("LEAVE_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW", "View leaves")));
        Permission permLeaveCancel = permissionRepository.findByName("LEAVE_CANCEL")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CANCEL", "Cancel leaves")));

        adminRole.getPermissions().addAll(Set.of(permAdminView, permLeaveApprove, permLeaveCreate, permLeaveApply, permLeaveView, permLeaveCancel));
        roleRepository.save(adminRole);

        managerRole.getPermissions().addAll(Set.of(permLeaveApprove, permLeaveView));
        roleRepository.save(managerRole);

        hrRole.getPermissions().addAll(Set.of(permLeaveApprove, permLeaveView));
        roleRepository.save(hrRole);

        employeeRole.getPermissions().addAll(Set.of(permLeaveApply, permLeaveCreate, permLeaveView, permLeaveCancel));
        roleRepository.save(employeeRole);

        // 2. Department
        department = new Department();
        department.setName("Engineering-" + runId);
        department = departmentRepository.save(department);

        // 3. Manager
        managerUser = new User();
        managerUser.setEmail("mgr-" + runId + "@stress.com");
        managerUser.setPassword(passwordEncoder.encode("MgrPass123!"));
        managerUser.setActive(true);
        managerUser.setAccountNonLocked(true);
        managerUser.getRoles().add(managerRole);
        managerUser = userRepository.save(managerUser);

        managerEmployee = new Employee();
        managerEmployee.setFirstName("Manager");
        managerEmployee.setLastName("Lead");
        managerEmployee.setEmployeeCode("MGR-" + runId);
        managerEmployee.setStatus("ACTIVE");
        managerEmployee.setSalary(new BigDecimal("110000.00"));
        managerEmployee.setDepartment(department);
        managerEmployee.setUser(managerUser);
        managerEmployee = employeeRepository.save(managerEmployee);
        managerUser.setEmployee(managerEmployee);
        managerUser = userRepository.save(managerUser);

        department.setManager(managerEmployee);
        department = departmentRepository.save(department);

        // 4. HR
        hrUser = new User();
        hrUser.setEmail("hr-" + runId + "@stress.com");
        hrUser.setPassword(passwordEncoder.encode("HrPass123!"));
        hrUser.setActive(true);
        hrUser.setAccountNonLocked(true);
        hrUser.getRoles().add(hrRole);
        hrUser = userRepository.save(hrUser);

        hrEmployee = new Employee();
        hrEmployee.setFirstName("HR");
        hrEmployee.setLastName("Specialist");
        hrEmployee.setEmployeeCode("HR-" + runId);
        hrEmployee.setStatus("ACTIVE");
        hrEmployee.setSalary(new BigDecimal("95000.00"));
        hrEmployee.setDepartment(department);
        hrEmployee.setUser(hrUser);
        hrEmployee = employeeRepository.save(hrEmployee);
        hrUser.setEmployee(hrEmployee);
        hrUser = userRepository.save(hrUser);

        // 5. Admin
        adminUser = new User();
        adminUser.setEmail("admin-" + runId + "@stress.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        adminEmployee = new Employee();
        adminEmployee.setFirstName("System");
        adminEmployee.setLastName("Admin");
        adminEmployee.setEmployeeCode("ADM-" + runId);
        adminEmployee.setStatus("ACTIVE");
        adminEmployee.setSalary(new BigDecimal("140000.00"));
        adminEmployee.setDepartment(department);
        adminEmployee.setUser(adminUser);
        adminEmployee = employeeRepository.save(adminEmployee);
        adminUser.setEmployee(adminEmployee);
        adminUser = userRepository.save(adminUser);

        // 6. Regular Employee
        employeeUser = new User();
        employeeUser.setEmail("emp-" + runId + "@stress.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.setAccountNonLocked(true);
        employeeUser.getRoles().add(employeeRole);
        employeeUser = userRepository.save(employeeUser);

        regularEmployee = new Employee();
        regularEmployee.setFirstName("Concurrent");
        regularEmployee.setLastName("Worker");
        regularEmployee.setEmployeeCode("EMP-" + runId);
        regularEmployee.setStatus("ACTIVE");
        regularEmployee.setSalary(new BigDecimal("80000.00"));
        regularEmployee.setDepartment(department);
        regularEmployee.setUser(employeeUser);
        regularEmployee = employeeRepository.save(regularEmployee);
        employeeUser.setEmployee(regularEmployee);
        employeeUser = userRepository.save(employeeUser);

        // 7. Leave Type & Balance
        annualLeaveType = new LeaveType();
        annualLeaveType.setName("Paid Vacation " + runId);
        annualLeaveType.setDescription("Annual Paid Vacation");
        annualLeaveType.setDefaultDays(25);
        annualLeaveType.setActive(true);
        annualLeaveType = leaveTypeRepository.save(annualLeaveType);

        employeeBalance = new LeaveBalance();
        employeeBalance.setEmployee(regularEmployee);
        employeeBalance.setLeaveType(annualLeaveType);
        employeeBalance.setYear(LocalDate.now().getYear());
        employeeBalance.setTotal(25);
        employeeBalance.setUsed(0);
        employeeBalance.setAvailable(25);
        employeeBalance = leaveBalanceRepository.saveAndFlush(employeeBalance);

        // Tokens
        adminToken = generateToken(adminUser, adminRole);
        managerToken = generateToken(managerUser, managerRole);
        hrToken = generateToken(hrUser, hrRole);
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
        cleanupDatabase();
        SecurityContextHolder.clearContext();
    }

    private void cleanupDatabase() {
        try {
            transactionReviewRepository.deleteAll();
            leaveRequestSignoffRepository.deleteAll();
            leaveRequestRepository.deleteAll();
            leaveBalanceRepository.deleteAll();
            securityEventRepository.deleteAll();
            auditLogRepository.deleteAll();
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Concurrent Overlapping Applications: 8 competing threads submit identical dates, exactly 1 succeeds")
    void testConcurrentOverlappingApplications_ExactlyOneSucceeds() throws Exception {
        LocalDate start = LocalDate.now().plusDays(14);
        LocalDate end = start.plusDays(3);

        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    long startNanos = System.nanoTime();
                    LeaveRequestDTO dto = new LeaveRequestDTO();
                    dto.setEmployeeId(regularEmployee.getId());
                    dto.setLeaveTypeId(annualLeaveType.getId());
                    dto.setStartDate(start);
                    dto.setEndDate(end);
                    dto.setReason("High-concurrency overlapping application stress test");

                    MvcResult result = mockMvc.perform(post("/api/leave-requests")
                                    .header("Authorization", "Bearer " + employeeToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                            .andReturn();

                    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    latencies.add(elapsedMs);

                    int status = result.getResponse().getStatus();
                    statuses.add(status);
                    if (status == 200 || status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 400 || status == 409) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        Collections.sort(latencies);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        long p50 = latencies.get((int) Math.ceil(latencies.size() * 0.50) - 1);
        long p90 = latencies.get((int) Math.ceil(latencies.size() * 0.90) - 1);
        long p95 = latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);
        long p99 = latencies.get((int) Math.ceil(latencies.size() * 0.99) - 1);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

        log.info("Raw concurrent leave application latencies (ms) [sorted, N={}]: {}", latencies.size(), latencies);
        log.info("Concurrent Application Stress Metrics [N={}]: min={}ms, avg={}ms, p50={}ms, p90={}ms, p95={}ms, p99={}ms, max={}ms",
                threads, min, String.format(Locale.ROOT, "%.2f", avg), p50, p90, p95, p99, max);

        assertEquals(1, successCount.get(), "Exactly one leave application must succeed among concurrent requests. Statuses: " + statuses);
        assertEquals(threads - 1, failureCount.get(), "All competing overlapping requests must be rejected. Statuses: " + statuses);

        List<LeaveRequest> storedRequests = leaveRequestRepository.findByEmployee(regularEmployee);
        assertEquals(1, storedRequests.size(), "Only 1 leave request must exist in database for the employee");
        assertEquals(LeaveStatus.PENDING, storedRequests.get(0).getStatus());
    }

    @Test
    @DisplayName("Dual Concurrent Approval: Simultaneous manager and HR approvals deduct leave balance exactly once")
    void testConcurrentDualApproval_DeductsBalanceOnce() throws Exception {
        // Create an initial leave request of 4 days
        LocalDate start = LocalDate.now().plusDays(30);
        LocalDate end = start.plusDays(3); // 4 days inclusive

        LeaveRequest request = new LeaveRequest();
        request.setEmployee(regularEmployee);
        request.setLeaveType(annualLeaveType);
        request.setStartDate(start);
        request.setEndDate(end);
        request.setReason("Dual approval concurrency test");
        request.setStatus(LeaveStatus.PENDING);
        request.setManagerApprovalStatus("PENDING");
        request.setHrApprovalStatus("PENDING");
        request = leaveRequestRepository.saveAndFlush(request);

        final Long leaveRequestId = request.getId();

        // 2 concurrent threads: Thread 1 Manager approves, Thread 2 Admin/HR approves
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger approvedResponses = new AtomicInteger(0);

        // Worker 1: Manager approval
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(patch("/api/leave-requests/" + leaveRequestId + "/approve")
                                .header("Authorization", "Bearer " + managerToken)
                                .param("comments", "Approved by manager"))
                        .andReturn();
                int st = result.getResponse().getStatus();
                if (st == 200) {
                    approvedResponses.incrementAndGet();
                }
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        // Worker 2: HR approval
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(patch("/api/leave-requests/" + leaveRequestId + "/approve")
                                .header("Authorization", "Bearer " + hrToken)
                                .param("comments", "Approved by HR"))
                        .andReturn();
                int st = result.getResponse().getStatus();
                if (st == 200) {
                    approvedResponses.incrementAndGet();
                }
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Approvals timed out");
        executor.shutdown();

        // Reload balance and verify no double deduction
        LeaveBalance updatedBalance = leaveBalanceRepository.findById(employeeBalance.getId()).orElseThrow();
        LeaveRequest finalRequest = leaveRequestRepository.findById(leaveRequestId).orElseThrow();

        // Total days for 4 days leave = 4
        // Used should never exceed 4 regardless of concurrency
        assertThat(updatedBalance.getUsed()).isLessThanOrEqualTo(4);
        assertThat(updatedBalance.getAvailable()).isGreaterThanOrEqualTo(21);
        assertEquals(25, updatedBalance.getTotal());
        assertEquals(updatedBalance.getTotal(), updatedBalance.getUsed() + updatedBalance.getAvailable(),
                "Balance equation must strictly hold: total = used + available");
    }

    @Test
    @DisplayName("Multi-Employee Concurrent Submissions: 4 distinct employees submit leaves simultaneously without conflict")
    void testMultiEmployeeConcurrentSubmissions_AllSucceedIndependently() throws Exception {
        int employeeCount = 4;
        List<Employee> employees = new ArrayList<>();
        List<String> tokens = new ArrayList<>();

        Role empRole = roleRepository.findByName("EMPLOYEE").orElseThrow();

        for (int i = 0; i < employeeCount; i++) {
            User u = new User();
            u.setEmail("multi-emp-" + i + "-" + runId + "@stress.com");
            u.setPassword(passwordEncoder.encode("Pass123!"));
            u.setActive(true);
            u.setAccountNonLocked(true);
            u.getRoles().add(empRole);
            u = userRepository.save(u);

            Employee e = new Employee();
            e.setFirstName("Worker" + i);
            e.setLastName("Multi");
            e.setEmployeeCode("WKR-" + i + "-" + runId);
            e.setStatus("ACTIVE");
            e.setSalary(new BigDecimal("75000.00"));
            e.setDepartment(department);
            e.setUser(u);
            e = employeeRepository.save(e);
            u.setEmployee(e);
            userRepository.save(u);

            LeaveBalance b = new LeaveBalance();
            b.setEmployee(e);
            b.setLeaveType(annualLeaveType);
            b.setYear(LocalDate.now().getYear());
            b.setTotal(20);
            b.setUsed(0);
            b.setAvailable(20);
            leaveBalanceRepository.saveAndFlush(b);

            employees.add(e);
            tokens.add(generateToken(u, empRole));
        }

        ExecutorService executor = Executors.newFixedThreadPool(employeeCount);
        CountDownLatch readyLatch = new CountDownLatch(employeeCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(employeeCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < employeeCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    LeaveRequestDTO dto = new LeaveRequestDTO();
                    dto.setEmployeeId(employees.get(index).getId());
                    dto.setLeaveTypeId(annualLeaveType.getId());
                    dto.setStartDate(LocalDate.now().plusDays(10 + index * 5));
                    dto.setEndDate(LocalDate.now().plusDays(12 + index * 5));
                    dto.setReason("Independent non-conflicting multi-employee application");

                    MvcResult result = mockMvc.perform(post("/api/leave-requests")
                                    .header("Authorization", "Bearer " + tokens.get(index))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                            .andReturn();

                    if (result.getResponse().getStatus() == 201) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Multi-employee execution timed out");
        executor.shutdown();

        assertEquals(employeeCount, successCount.get(), "All multi-employee concurrent applications must succeed");
    }
}
