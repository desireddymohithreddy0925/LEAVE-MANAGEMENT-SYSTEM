package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentLeaveApprovalIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private User adminUser;
    private String adminToken;
    private Employee testEmployee;
    private LeaveType testLeaveType;
    private LeaveBalance testBalance;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        cleanup();

        // 1. Setup Admin Role and User
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));

        List<String> perms = List.of("LEAVE_APPROVE", "LEAVE_REJECT", "LEAVE_CANCEL", "LEAVE_VIEW_ALL", "LEAVE_BALANCE_MANAGE");
        for (String permName : perms) {
            Permission p = permissionRepository.findByName(permName)
                    .orElseGet(() -> permissionRepository.save(new Permission(permName, permName + " permission")));
            adminRole.getPermissions().add(p);
        }
        roleRepository.save(adminRole);

        String uniqueAdminEmail = "admin-concurrent-" + UUID.randomUUID() + "@test.com";
        adminUser = new User();
        adminUser.setEmail(uniqueAdminEmail);
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.setRoles(Set.of(adminRole));
        adminUser = userRepository.save(adminUser);

        adminToken = jwtUtils.generateTokenFromUsername(adminUser.getEmail());

        // 2. Setup Employee User
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        String uniqueEmpEmail = "emp-concurrent-" + UUID.randomUUID() + "@test.com";
        User empUser = new User();
        empUser.setEmail(uniqueEmpEmail);
        empUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        empUser.setActive(true);
        empUser.setAccountNonLocked(true);
        empUser.setRoles(Set.of(empRole));
        empUser = userRepository.save(empUser);

        Employee employee = new Employee();
        employee.setFirstName("Jane");
        employee.setLastName("Concurrent");
        employee.setEmployeeCode("CONCUR_" + System.currentTimeMillis() % 10000);
        employee.setPhone("9876543210");
        employee.setStatus("ACTIVE");
        employee.setSalary(new BigDecimal("60000.00"));
        employee.setUser(empUser);
        testEmployee = employeeRepository.save(employee);

        // 3. Setup LeaveType
        LeaveType lt = new LeaveType();
        lt.setName("Annual Vacation " + UUID.randomUUID().toString().substring(0, 5));
        lt.setDefaultDays(20);
        lt.setActive(true);
        testLeaveType = leaveTypeRepository.save(lt);

        // 4. Setup LeaveBalance
        LeaveBalance bal = new LeaveBalance();
        bal.setEmployee(testEmployee);
        bal.setLeaveType(testLeaveType);
        bal.setYear(2026);
        bal.setTotal(20);
        bal.setUsed(0);
        bal.setAvailable(20);
        testBalance = leaveBalanceRepository.save(bal);
    }

    @AfterEach
    public void cleanup() {
        transactionReviewRepository.deleteAll();
        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        employeeRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        userRepository.deleteAll();
    }

    private LeaveRequest createPendingRequest(LocalDate start, LocalDate end) {
        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(testEmployee);
        lr.setLeaveType(testLeaveType);
        lr.setStartDate(start);
        lr.setEndDate(end);
        lr.setStatus(LeaveStatus.PENDING);
        lr.setReason("Test concurrent leave");
        return leaveRequestRepository.save(lr);
    }

    @Test
    public void concurrentSimultaneousApprovals_sameRequest_exactlyOneSucceeds_andBalanceDeductedOnce() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 6); // Tuesday
        LocalDate end = LocalDate.of(2026, 10, 8);   // Thursday (3 days)
        LeaveRequest lr = createPendingRequest(start, end);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                                    .header("Authorization", "Bearer " + adminToken)
                                    .contentType(MediaType.APPLICATION_JSON))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    statusCodes.add(status);
                    if (status == 200) {
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

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one approval must return HTTP 200 OK. Codes: " + statusCodes);
        assertEquals(1, failureCount.get(), "The competing approval must return HTTP 400 Bad Request or 409 Conflict. Codes: " + statusCodes);

        LeaveBalance updatedBal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(17, updatedBal.getAvailable(), "Leave balance available should be decremented by exactly 3 days once");
        assertEquals(3, updatedBal.getUsed(), "Leave balance used should be incremented by exactly 3 days once");

        LeaveRequest finalReq = leaveRequestRepository.findById(lr.getId()).orElseThrow();
        assertEquals(LeaveStatus.APPROVED, finalReq.getStatus());
    }

    @Test
    public void concurrentSimultaneousApprovals_fiveThreads_exactlyOneSucceeds_othersFailCleanly() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 13); // Tuesday
        LocalDate end = LocalDate.of(2026, 10, 15);  // Thursday (3 days)
        LeaveRequest lr = createPendingRequest(start, end);

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                                    .header("Authorization", "Bearer " + adminToken)
                                    .contentType(MediaType.APPLICATION_JSON))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    statusCodes.add(status);
                    if (status == 200) {
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

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one approval out of 5 must return HTTP 200 OK. Codes: " + statusCodes);
        assertEquals(4, failureCount.get(), "All other 4 approvals must return HTTP 400 or 409. Codes: " + statusCodes);

        LeaveBalance updatedBal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(17, updatedBal.getAvailable(), "Balance must only be deducted once");
        assertEquals(3, updatedBal.getUsed(), "Used balance must only be incremented once");
    }

    @Test
    public void concurrentSimultaneousApprovals_differentHttpMethods_putAndPatch_serializeCleanly() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 20); // Tuesday
        LocalDate end = LocalDate.of(2026, 10, 22);   // Thursday (3 days)
        LeaveRequest lr = createPendingRequest(start, end);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: PUT
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                int s = res.getResponse().getStatus();
                statusCodes.add(s);
                if (s == 200) successCount.incrementAndGet();
                else failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: PATCH
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(patch("/api/leave-requests/" + lr.getId() + "/approve")
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                int s = res.getResponse().getStatus();
                statusCodes.add(s);
                if (s == 200) successCount.incrementAndGet();
                else failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one method (PUT or PATCH) must succeed. Codes: " + statusCodes);
        assertEquals(1, failureCount.get(), "The competing method must fail with 400 or 409. Codes: " + statusCodes);

        LeaveBalance updatedBal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(17, updatedBal.getAvailable(), "Balance must only be deducted once");
    }

    @Test
    public void concurrentApprovalAndCancellation_onSameRequest_maintainsBalanceIntegrity() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 3); // Tuesday
        LocalDate end = LocalDate.of(2026, 11, 5);   // Thursday (3 days)
        LeaveRequest lr = createPendingRequest(start, end);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger approveStatus = new AtomicInteger(0);
        AtomicInteger cancelStatus = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                approveStatus.set(res.getResponse().getStatus());
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/cancel")
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                cancelStatus.set(res.getResponse().getStatus());
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        LeaveBalance updatedBal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        LeaveRequest finalReq = leaveRequestRepository.findById(lr.getId()).orElseThrow();

        // Valid outcomes:
        // 1. Approve won (200), Cancel failed or cancelled the approved request (restoring balance)
        // 2. Cancel won (200), Approve failed with 400 (balance untouched)
        assertTrue(finalReq.getStatus() == LeaveStatus.APPROVED || finalReq.getStatus() == LeaveStatus.CANCELLED);
        assertTrue(updatedBal.getAvailable() == 17 || updatedBal.getAvailable() == 20,
                "Balance available must be either 17 (approved) or 20 (cancelled), got: " + updatedBal.getAvailable());
        assertEquals(20, updatedBal.getAvailable() + updatedBal.getUsed(), "Total leave balance must remain invariant at 20");
    }

    @Test
    public void concurrentApprovalAndRejection_onSameRequest_exactlyOneWins() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 10); // Tuesday
        LocalDate end = LocalDate.of(2026, 11, 12);   // Thursday (3 days)
        LeaveRequest lr = createPendingRequest(start, end);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger approveStatus = new AtomicInteger(0);
        AtomicInteger rejectStatus = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                approveStatus.set(res.getResponse().getStatus());
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/reject")
                                .param("reason", "Project deadline conflicts")
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                rejectStatus.set(res.getResponse().getStatus());
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        // Exactly one should succeed with 200, the other should fail with 400/409
        boolean approveWon = (approveStatus.get() == 200 && (rejectStatus.get() == 400 || rejectStatus.get() == 409));
        boolean rejectWon = (rejectStatus.get() == 200 && (approveStatus.get() == 400 || approveStatus.get() == 409));

        assertTrue(approveWon || rejectWon, "Either approve or reject must win exclusively. Approve: " + approveStatus.get() + ", Reject: " + rejectStatus.get());

        LeaveRequest finalReq = leaveRequestRepository.findById(lr.getId()).orElseThrow();
        LeaveBalance updatedBal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();

        if (approveWon) {
            assertEquals(LeaveStatus.APPROVED, finalReq.getStatus());
            assertEquals(17, updatedBal.getAvailable());
            assertEquals(3, updatedBal.getUsed());
        } else {
            assertEquals(LeaveStatus.REJECTED, finalReq.getStatus());
            assertEquals(20, updatedBal.getAvailable());
            assertEquals(0, updatedBal.getUsed());
        }
    }

    @Test
    public void concurrentApprovals_overlappingRequestsForSameEmployee_onlyOneApproved() throws Exception {
        // Request 1: Oct 12 - 14 (Mon-Wed, 3 days)
        LocalDate start1 = LocalDate.of(2026, 10, 12);
        LocalDate end1 = LocalDate.of(2026, 10, 14);
        LeaveRequest lr1 = createPendingRequest(start1, end1);

        // Request 2: Oct 13 - 15 (Tue-Thu, 3 days, overlaps with Request 1 on Oct 13 & 14)
        LocalDate start2 = LocalDate.of(2026, 10, 13);
        LocalDate end2 = LocalDate.of(2026, 10, 15);
        LeaveRequest lr2 = createPendingRequest(start2, end2);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr1.getId() + "/approve")
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                int s = res.getResponse().getStatus();
                statusCodes.add(s);
                if (s == 200) successCount.incrementAndGet();
                else failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr2.getId() + "/approve")
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                int s = res.getResponse().getStatus();
                statusCodes.add(s);
                if (s == 200) successCount.incrementAndGet();
                else failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Only one overlapping request should be approved. Codes: " + statusCodes);
        assertEquals(1, failureCount.get(), "The conflicting overlapping request must fail approval. Codes: " + statusCodes);

        LeaveRequest req1 = leaveRequestRepository.findById(lr1.getId()).orElseThrow();
        LeaveRequest req2 = leaveRequestRepository.findById(lr2.getId()).orElseThrow();

        // Exactly one is APPROVED, the other is still PENDING
        assertTrue((req1.getStatus() == LeaveStatus.APPROVED && req2.getStatus() == LeaveStatus.PENDING) ||
                        (req2.getStatus() == LeaveStatus.APPROVED && req1.getStatus() == LeaveStatus.PENDING),
                "One request must be APPROVED and the other PENDING");

        LeaveBalance updatedBal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(17, updatedBal.getAvailable(), "Balance must be deducted for exactly 1 request (3 days)");
        assertEquals(3, updatedBal.getUsed(), "Used days must reflect only 1 approved request");
    }

    @Test
    public void concurrentApprovals_competingForInsufficientRemainingBalance_onlyOneFits() throws Exception {
        // Set available balance to 3 days
        testBalance.setTotal(10);
        testBalance.setUsed(7);
        testBalance.setAvailable(3);
        leaveBalanceRepository.save(testBalance);

        // Request 1: Nov 16-17 (Mon-Tue, 2 days)
        LocalDate start1 = LocalDate.of(2026, 11, 16);
        LocalDate end1 = LocalDate.of(2026, 11, 17);
        LeaveRequest lr1 = createPendingRequest(start1, end1);

        // Request 2: Nov 23-24 (Mon-Tue, 2 days, non-overlapping)
        // Total required = 4 days, but available is only 3!
        LocalDate start2 = LocalDate.of(2026, 11, 23);
        LocalDate end2 = LocalDate.of(2026, 11, 24);
        LeaveRequest lr2 = createPendingRequest(start2, end2);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr1.getId() + "/approve")
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                int s = res.getResponse().getStatus();
                statusCodes.add(s);
                if (s == 200) successCount.incrementAndGet();
                else failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr2.getId() + "/approve")
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                int s = res.getResponse().getStatus();
                statusCodes.add(s);
                if (s == 200) successCount.incrementAndGet();
                else failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Only one request should succeed due to balance limitation. Codes: " + statusCodes);
        assertEquals(1, failureCount.get(), "The other request must fail with 400 Insufficient balance. Codes: " + statusCodes);

        LeaveBalance updatedBal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(1, updatedBal.getAvailable(), "Balance must be 3 - 2 = 1");
        assertEquals(9, updatedBal.getUsed(), "Used balance must be 7 + 2 = 9");
        assertTrue(updatedBal.getAvailable() >= 0, "Balance must never drop below 0");
    }
}
