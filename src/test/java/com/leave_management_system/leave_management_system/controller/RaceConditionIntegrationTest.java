package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Comprehensive Race-Condition Integration Test Suite for the Leave Management System.
 *
 * <p>Validates correctness under high-concurrency workloads by verifying that all critical
 * state transitions, balance accounting, and uniqueness invariants hold when multiple
 * threads race against the same shared resources simultaneously.</p>
 *
 * <h3>Race-Condition Scenarios Covered:</h3>
 * <ol>
 *   <li>Balance depletion: 10 concurrent approvals on same request — exactly 1 wins</li>
 *   <li>Approve + Reject race on same PENDING request</li>
 *   <li>Approve + Cancel race on same PENDING request</li>
 *   <li>Reject + Cancel race on same PENDING request</li>
 *   <li>Concurrent idempotent submissions with the same Idempotency-Key (5 threads)</li>
 *   <li>High-concurrency (10 threads) application storm with scarce balance</li>
 *   <li>Mixed actors: admin approve vs employee cancel simultaneously</li>
 *   <li>Write-skew: two employees drain their individual balances simultaneously</li>
 *   <li>Concurrent re-application after cancellation refund — no double-refund</li>
 *   <li>3 concurrent cancellations of an APPROVED request — balance refunded once</li>
 *   <li>Concurrent approval of 2 distinct requests exhausts balance to zero</li>
 *   <li>Phantom-read guard: balance invariant enforced under pessimistic lock</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
public class RaceConditionIntegrationTest {

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private LeaveBalanceRepository leaveBalanceRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private SecurityEventRepository securityEventRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private LoginHistoryRepository loginHistoryRepository;
    @Autowired private AuthorizationAuditLogRepository authorizationAuditLogRepository;
    @Autowired private TransactionReviewRepository transactionReviewRepository;
    @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private User adminUser;
    private String adminToken;
    private Employee employee1;
    private String employee1Token;
    private Employee employee2;
    private String employee2Token;
    private LeaveType annualLeaveType;
    private LeaveBalance employee1Balance;
    private LeaveBalance employee2Balance;

    // ─────────────────────────── Setup / Teardown ────────────────────────────

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        cleanup();

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role empRole = roleRepository.findByName("EMPLOYEE")
                .orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        for (String name : List.of("LEAVE_APPROVE","LEAVE_REJECT","LEAVE_CANCEL",
                "LEAVE_VIEW_ALL","LEAVE_BALANCE_MANAGE","LEAVE_CREATE")) {
            Permission p = permissionRepository.findByName(name)
                    .orElseGet(() -> permissionRepository.save(new Permission(name, name)));
            adminRole.getPermissions().add(p);
        }
        roleRepository.save(adminRole);

        for (String name : List.of("LEAVE_CREATE","LEAVE_CANCEL")) {
            Permission p = permissionRepository.findByName(name)
                    .orElseGet(() -> permissionRepository.save(new Permission(name, name)));
            empRole.getPermissions().add(p);
        }
        roleRepository.save(empRole);

        adminUser = new User();
        adminUser.setEmail("race-admin-" + UUID.randomUUID() + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("Admin@1234"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.setRoles(Set.of(adminRole));
        adminUser = userRepository.save(adminUser);
        adminToken = jwtUtils.generateTokenFromUsername(adminUser.getEmail());

        User empUser1 = new User();
        empUser1.setEmail("race-emp1-" + UUID.randomUUID() + "@test.com");
        empUser1.setPassword(passwordEncoder.encode("Emp@1234"));
        empUser1.setActive(true);
        empUser1.setAccountNonLocked(true);
        empUser1.setRoles(Set.of(empRole));
        empUser1 = userRepository.save(empUser1);
        employee1Token = jwtUtils.generateTokenFromUsername(empUser1.getEmail());
        employee1 = new Employee();
        employee1.setFirstName("Alice"); employee1.setLastName("Race");
        employee1.setEmployeeCode("RACE1_" + System.currentTimeMillis() % 100000);
        employee1.setPhone("1111111111"); employee1.setStatus("ACTIVE");
        employee1.setSalary(new BigDecimal("50000.00")); employee1.setUser(empUser1);
        employee1 = employeeRepository.save(employee1);

        User empUser2 = new User();
        empUser2.setEmail("race-emp2-" + UUID.randomUUID() + "@test.com");
        empUser2.setPassword(passwordEncoder.encode("Emp@1234"));
        empUser2.setActive(true);
        empUser2.setAccountNonLocked(true);
        empUser2.setRoles(Set.of(empRole));
        empUser2 = userRepository.save(empUser2);
        employee2Token = jwtUtils.generateTokenFromUsername(empUser2.getEmail());
        employee2 = new Employee();
        employee2.setFirstName("Bob"); employee2.setLastName("Race");
        employee2.setEmployeeCode("RACE2_" + System.currentTimeMillis() % 100000);
        employee2.setPhone("2222222222"); employee2.setStatus("ACTIVE");
        employee2.setSalary(new BigDecimal("50000.00")); employee2.setUser(empUser2);
        employee2 = employeeRepository.save(employee2);

        annualLeaveType = new LeaveType();
        annualLeaveType.setName("Annual-Race-" + UUID.randomUUID().toString().substring(0,6));
        annualLeaveType.setDefaultDays(20); annualLeaveType.setActive(true);
        annualLeaveType = leaveTypeRepository.save(annualLeaveType);

        employee1Balance = createBalance(employee1, annualLeaveType, 20);
        employee2Balance = createBalance(employee2, annualLeaveType, 20);
    }

    @AfterEach
    public void cleanup() {
        transactionReviewRepository.deleteAll();
        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        employeeRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ──────────────────────────── Helpers ────────────────────────────────────

    private LeaveBalance createBalance(Employee emp, LeaveType lt, int days) {
        LeaveBalance b = new LeaveBalance();
        b.setEmployee(emp); b.setLeaveType(lt); b.setYear(2027);
        b.setTotal(days); b.setUsed(0); b.setAvailable(days);
        return leaveBalanceRepository.save(b);
    }

    private LeaveRequest createPendingRequest(Employee emp, LocalDate start, LocalDate end) {
        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(emp); lr.setLeaveType(annualLeaveType);
        lr.setStartDate(start); lr.setEndDate(end);
        lr.setStatus(LeaveStatus.PENDING); lr.setReason("Race condition test");
        return leaveRequestRepository.save(lr);
    }

    private String leaveRequestPayload(Employee emp, LocalDate start, LocalDate end) throws Exception {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(emp.getId()); dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(start); dto.setEndDate(end); dto.setReason("Race condition leave");
        return objectMapper.writeValueAsString(dto);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 1 – 10 concurrent approvals on one PENDING request: exactly 1 wins
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void balanceDepleted_tenConcurrentApprovalAttempts_exactlyOneSucceeds_balanceDeductedOnce()
            throws Exception {
        employee1Balance.setTotal(3); employee1Balance.setAvailable(3); employee1Balance.setUsed(0);
        leaveBalanceRepository.save(employee1Balance);
        LeaveRequest lr = createPendingRequest(employee1, LocalDate.of(2027,3,1), LocalDate.of(2027,3,3));

        int threads = 10;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        List<Integer> codes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            ex.submit(() -> {
                try {
                    start.await();
                    MvcResult r = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                            .header("Authorization","Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)).andReturn();
                    int s = r.getResponse().getStatus(); codes.add(s);
                    if (s == 200) ok.incrementAndGet(); else fail.incrementAndGet();
                } catch (Exception e) { fail.incrementAndGet(); } finally { done.countDown(); }
            });
        }
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS), "10-thread approval storm timed out");
        ex.shutdown();

        assertEquals(1, ok.get(), "Exactly 1 of 10 approvals must succeed. Codes: " + codes);
        assertEquals(LeaveStatus.APPROVED, leaveRequestRepository.findById(lr.getId()).orElseThrow().getStatus());
        LeaveBalance b = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow();
        assertEquals(0, b.getAvailable(), "Balance must reach 0 (deducted once). Available: " + b.getAvailable());
        assertEquals(3, b.getUsed(), "Used must be 3");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 2 – Approve + Reject race on same PENDING request
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void approveAndRejectRace_exactlyOneWins_balanceConsistentWithWinner() throws Exception {
        LeaveRequest lr = createPendingRequest(employee1, LocalDate.of(2027,3,8), LocalDate.of(2027,3,10));
        int initAvail = employee1Balance.getAvailable();

        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(2);
        AtomicInteger ok = new AtomicInteger();
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());

        new Thread(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int s = r.getResponse().getStatus();
                if (s == 200) { ok.incrementAndGet(); outcomes.add("APPROVE_WIN"); }
                else outcomes.add("APPROVE_LOSE:" + s);
            } catch (Exception e) { outcomes.add("APPROVE_EX"); } finally { done.countDown(); }
        }, "race-approve").start();

        new Thread(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/reject?reason=Budget")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int s = r.getResponse().getStatus();
                if (s == 200) { ok.incrementAndGet(); outcomes.add("REJECT_WIN"); }
                else outcomes.add("REJECT_LOSE:" + s);
            } catch (Exception e) { outcomes.add("REJECT_EX"); } finally { done.countDown(); }
        }, "race-reject").start();

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Approve+Reject race timed out");

        assertEquals(1, ok.get(), "Exactly one must win. Outcomes: " + outcomes);
        LeaveRequest saved = leaveRequestRepository.findById(lr.getId()).orElseThrow();
        assertTrue(saved.getStatus() == LeaveStatus.APPROVED || saved.getStatus() == LeaveStatus.REJECTED,
                "Must be APPROVED or REJECTED, not: " + saved.getStatus());
        LeaveBalance b = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow();
        if (saved.getStatus() == LeaveStatus.APPROVED) {
            assertEquals(initAvail - 3, b.getAvailable(), "Balance -3 when APPROVE wins");
            assertEquals(3, b.getUsed());
        } else {
            assertEquals(initAvail, b.getAvailable(), "Balance untouched when REJECT wins");
            assertEquals(0, b.getUsed());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 3 – Approve + Cancel race on same PENDING request
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void approveAndCancelRace_exactlyOneWins_noBalanceCorruption() throws Exception {
        LeaveRequest lr = createPendingRequest(employee1, LocalDate.of(2027,3,15), LocalDate.of(2027,3,16));
        int initAvail = employee1Balance.getAvailable();

        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(2);
        AtomicInteger ok = new AtomicInteger();
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());

        new Thread(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int s = r.getResponse().getStatus();
                if (s == 200) { ok.incrementAndGet(); outcomes.add("APPROVE_WIN"); }
                else outcomes.add("APPROVE_LOSE:" + s);
            } catch (Exception e) { outcomes.add("APPROVE_EX"); } finally { done.countDown(); }
        }, "race-approve").start();

        new Thread(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/cancel")
                        .header("Authorization","Bearer " + employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int s = r.getResponse().getStatus();
                if (s == 200) { ok.incrementAndGet(); outcomes.add("CANCEL_WIN"); }
                else outcomes.add("CANCEL_LOSE:" + s);
            } catch (Exception e) { outcomes.add("CANCEL_EX"); } finally { done.countDown(); }
        }, "race-cancel").start();

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Approve+Cancel race timed out");

        assertEquals(1, ok.get(), "Exactly one must win. Outcomes: " + outcomes);
        LeaveRequest saved = leaveRequestRepository.findById(lr.getId()).orElseThrow();
        assertTrue(saved.getStatus() == LeaveStatus.APPROVED || saved.getStatus() == LeaveStatus.CANCELLED,
                "Must be APPROVED or CANCELLED, not: " + saved.getStatus());
        LeaveBalance b = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow();
        if (saved.getStatus() == LeaveStatus.APPROVED) {
            assertEquals(initAvail - 2, b.getAvailable(), "Balance -2 when APPROVE wins");
        } else {
            assertEquals(initAvail, b.getAvailable(), "Balance untouched when CANCEL wins from PENDING");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 4 – Reject + Cancel race on same PENDING request
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void rejectAndCancelRace_exactlyOneWins_balanceAlwaysUntouched() throws Exception {
        LeaveRequest lr = createPendingRequest(employee1, LocalDate.of(2027,3,22), LocalDate.of(2027,3,24));
        int initAvail = employee1Balance.getAvailable();

        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(2);
        AtomicInteger ok = new AtomicInteger();
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());

        new Thread(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/reject?reason=Staffing")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int s = r.getResponse().getStatus();
                if (s == 200) { ok.incrementAndGet(); outcomes.add("REJECT_WIN"); }
                else outcomes.add("REJECT_LOSE:" + s);
            } catch (Exception e) { outcomes.add("REJECT_EX"); } finally { done.countDown(); }
        }, "race-reject").start();

        new Thread(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/cancel")
                        .header("Authorization","Bearer " + employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int s = r.getResponse().getStatus();
                if (s == 200) { ok.incrementAndGet(); outcomes.add("CANCEL_WIN"); }
                else outcomes.add("CANCEL_LOSE:" + s);
            } catch (Exception e) { outcomes.add("CANCEL_EX"); } finally { done.countDown(); }
        }, "race-cancel").start();

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Reject+Cancel race timed out");

        assertEquals(1, ok.get(), "Exactly one must win. Outcomes: " + outcomes);
        LeaveBalance b = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow();
        assertEquals(initAvail, b.getAvailable(), "Balance must always be untouched for REJECT/CANCEL on PENDING");
        assertEquals(0, b.getUsed());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 5 – Concurrent idempotent submissions (same Idempotency-Key)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void concurrentIdempotentSubmissions_sameKey_exactlyOneProcessed_othersCached() throws Exception {
        String idemKey = "idem-race-" + UUID.randomUUID();
        String payload = leaveRequestPayload(employee1, LocalDate.of(2027,4,7), LocalDate.of(2027,4,9));

        int threads = 5;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger(), conflicts = new AtomicInteger(), errors = new AtomicInteger();
        List<Integer> codes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            ex.submit(() -> {
                try {
                    start.await();
                    MvcResult r = mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization","Bearer " + adminToken)
                            .header("Idempotency-Key", idemKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload)).andReturn();
                    int s = r.getResponse().getStatus(); codes.add(s);
                    if (s == 200 || s == 201) ok.incrementAndGet();
                    else if (s == 409) conflicts.incrementAndGet();
                    else errors.incrementAndGet();
                } catch (Exception e) { errors.incrementAndGet(); } finally { done.countDown(); }
            });
        }
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS), "Idempotent race timed out");
        ex.shutdown();

        assertEquals(0, errors.get(), "No unexpected error responses. Codes: " + codes);
        assertTrue(ok.get() >= 1, "At least one request must succeed. Codes: " + codes);
        assertEquals(threads, ok.get() + conflicts.get(), "All responses must be success or 409 in-progress conflict. Codes: " + codes);
        List<LeaveRequest> reqs = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(1, reqs.size(),
                "Exactly 1 leave request must exist in DB despite 5 concurrent idempotent submissions. Found: " + reqs.size());

        // Subsequent sequential call with the same key must return the cached 201/200 response with Idempotency-Replayed
        MvcResult replayResult = mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andReturn();
        int replayStatus = replayResult.getResponse().getStatus();
        assertTrue(replayStatus == 200 || replayStatus == 201, "Replayed request must succeed: " + replayStatus);
        assertEquals("true", replayResult.getResponse().getHeader("Idempotency-Replayed"));
        assertEquals(1, leaveRequestRepository.findByEmployee(employee1).size(),
                "Still exactly 1 leave request after replay");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 6 – 10-thread application storm with scarce balance
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void highConcurrencyApplicationStorm_scarceBalance_exactlyOneSucceeds_balanceNonNegative()
            throws Exception {
        employee1Balance.setTotal(3); employee1Balance.setAvailable(3); employee1Balance.setUsed(0);
        leaveBalanceRepository.save(employee1Balance);

        LocalDate s = LocalDate.of(2027,4,14), e = LocalDate.of(2027,4,16);
        int threads = 10;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        List<Integer> codes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            ex.submit(() -> {
                try {
                    start.await();
                    MvcResult r = mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization","Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(leaveRequestPayload(employee1, s, e))).andReturn();
                    int st = r.getResponse().getStatus(); codes.add(st);
                    if (st == 200 || st == 201) ok.incrementAndGet(); else fail.incrementAndGet();
                } catch (Exception ex2) { fail.incrementAndGet(); } finally { done.countDown(); }
            });
        }
        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS), "High-concurrency storm timed out");
        ex.shutdown();

        assertEquals(1, ok.get(), "Exactly 1 of 10 must succeed with scarce balance. Codes: " + codes);
        assertEquals(9, fail.get(), "9 must be rejected. Codes: " + codes);
        LeaveBalance b = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow();
        assertTrue(b.getAvailable() >= 0, "Balance must never go negative. Available: " + b.getAvailable());
        assertEquals(1, leaveRequestRepository.findByEmployee(employee1).size(),
                "Exactly 1 request must be persisted");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 7 – Mixed actors: admin approve vs employee cancel
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void mixedActors_adminApproveVsEmployeeCancel_exactlyOneWins_stateAndBalanceConsistent()
            throws Exception {
        LeaveRequest lr = createPendingRequest(employee1, LocalDate.of(2027,4,21), LocalDate.of(2027,4,25));
        int initAvail = employee1Balance.getAvailable();

        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(2);
        AtomicInteger ok = new AtomicInteger();
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());

        new Thread(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int s = r.getResponse().getStatus();
                if (s == 200) { ok.incrementAndGet(); outcomes.add("ADMIN_APPROVE_WIN"); }
                else outcomes.add("ADMIN_APPROVE_LOSE:" + s);
            } catch (Exception e) { outcomes.add("ADMIN_APPROVE_EX"); } finally { done.countDown(); }
        }, "admin-approve").start();

        new Thread(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/cancel")
                        .header("Authorization","Bearer " + employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int s = r.getResponse().getStatus();
                if (s == 200) { ok.incrementAndGet(); outcomes.add("EMP_CANCEL_WIN"); }
                else outcomes.add("EMP_CANCEL_LOSE:" + s);
            } catch (Exception e) { outcomes.add("EMP_CANCEL_EX"); } finally { done.countDown(); }
        }, "emp-cancel").start();

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Mixed-actors race timed out");

        assertEquals(1, ok.get(), "Exactly one must win. Outcomes: " + outcomes);
        LeaveRequest saved = leaveRequestRepository.findById(lr.getId()).orElseThrow();
        assertTrue(saved.getStatus() == LeaveStatus.APPROVED || saved.getStatus() == LeaveStatus.CANCELLED,
                "Must be APPROVED or CANCELLED, not: " + saved.getStatus());
        LeaveBalance b = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow();
        if (saved.getStatus() == LeaveStatus.APPROVED) {
            assertTrue(b.getAvailable() < initAvail, "Balance must decrease when APPROVE wins");
        } else {
            assertEquals(initAvail, b.getAvailable(), "Balance untouched when CANCEL wins from PENDING");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 8 – Write-skew: two independent employees drain same leave type
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void writeSkew_twoEmployees_sameDates_eachHasOwnBalance_bothSucceed_individuallyDeducted()
            throws Exception {
        employee1Balance.setTotal(3); employee1Balance.setAvailable(3); employee1Balance.setUsed(0);
        leaveBalanceRepository.save(employee1Balance);
        employee2Balance.setTotal(3); employee2Balance.setAvailable(3); employee2Balance.setUsed(0);
        leaveBalanceRepository.save(employee2Balance);

        LocalDate s = LocalDate.of(2027,5,4), e = LocalDate.of(2027,5,6);
        LeaveRequest r1 = createPendingRequest(employee1, s, e);
        LeaveRequest r2 = createPendingRequest(employee2, s, e);

        ExecutorService ex = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(2);
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        List<Integer> codes = Collections.synchronizedList(new ArrayList<>());

        ex.submit(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + r1.getId() + "/approve")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int st = r.getResponse().getStatus(); codes.add(st);
                if (st == 200) ok.incrementAndGet(); else fail.incrementAndGet();
            } catch (Exception ex2) { fail.incrementAndGet(); } finally { done.countDown(); }
        });
        ex.submit(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + r2.getId() + "/approve")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int st = r.getResponse().getStatus(); codes.add(st);
                if (st == 200) ok.incrementAndGet(); else fail.incrementAndGet();
            } catch (Exception ex2) { fail.incrementAndGet(); } finally { done.countDown(); }
        });

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Write-skew scenario timed out");
        ex.shutdown();

        assertEquals(2, ok.get(), "Both distinct employees must succeed. Codes: " + codes);
        assertEquals(0, leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow().getAvailable(),
                "Employee1 balance must be 0");
        assertEquals(0, leaveBalanceRepository.findById(employee2Balance.getId()).orElseThrow().getAvailable(),
                "Employee2 balance must be 0");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 9 – Concurrent re-application after cancellation refund
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void concurrentReApplicationAfterRefund_exactlyOneSucceeds_noDoubleRefund() throws Exception {
        LeaveRequest original = createPendingRequest(employee1, LocalDate.of(2027,5,11), LocalDate.of(2027,5,13));

        mockMvc.perform(put("/api/leave-requests/" + original.getId() + "/approve")
                .header("Authorization","Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertEquals(200, result.getResponse().getStatus()));
        assertEquals(17, leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow().getAvailable(),
                "Balance must be 17 after approval");

        mockMvc.perform(put("/api/leave-requests/" + original.getId() + "/cancel")
                .header("Authorization","Bearer " + employee1Token).contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertEquals(200, result.getResponse().getStatus()));
        assertEquals(20, leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow().getAvailable(),
                "Balance must be refunded to 20");

        LocalDate rs = LocalDate.of(2027,5,11), re = LocalDate.of(2027,5,13);
        ExecutorService ex = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(2);
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        List<Integer> codes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < 2; i++) {
            ex.submit(() -> {
                try {
                    start.await();
                    MvcResult r = mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization","Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(leaveRequestPayload(employee1, rs, re))).andReturn();
                    int st = r.getResponse().getStatus(); codes.add(st);
                    if (st == 200 || st == 201) ok.incrementAndGet(); else fail.incrementAndGet();
                } catch (Exception ex2) { fail.incrementAndGet(); } finally { done.countDown(); }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Re-application race timed out");
        ex.shutdown();

        assertEquals(1, ok.get(), "Exactly 1 re-application must succeed. Codes: " + codes);
        assertEquals(1, fail.get(), "The competing re-application must fail. Codes: " + codes);

        List<LeaveRequest> all = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(2, all.size(), "Must have 2 requests: CANCELLED + PENDING");
        assertEquals(1, all.stream().filter(r -> r.getStatus() == LeaveStatus.CANCELLED).count(),
                "Exactly 1 CANCELLED");
        assertEquals(1, all.stream().filter(r -> r.getStatus() == LeaveStatus.PENDING).count(),
                "Exactly 1 PENDING");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 10 – 3 concurrent cancellations of an APPROVED request
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void threeConcurrentCancellations_approvedRequest_exactlyOneSucceeds_balanceRefundedOnce()
            throws Exception {
        LocalDate startLeave = LocalDate.of(2027, 5, 17); // Monday
        LocalDate endLeave = LocalDate.of(2027, 5, 21);   // Friday (exactly 5 working days)
        LeaveRequest lr = createPendingRequest(employee1, startLeave, endLeave);
        int availBeforeApproval = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow().getAvailable();
        mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                .header("Authorization","Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertEquals(200, result.getResponse().getStatus()));

        int availAfterApproval = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow().getAvailable();
        int deducted = availBeforeApproval - availAfterApproval;
        assertEquals(5, deducted, "Approval must have deducted exactly 5 working days");

        int threads = 3;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        List<Integer> codes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            ex.submit(() -> {
                try {
                    start.await();
                    MvcResult r = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/cancel")
                            .header("Authorization","Bearer " + employee1Token)
                            .contentType(MediaType.APPLICATION_JSON)).andReturn();
                    int s = r.getResponse().getStatus(); codes.add(s);
                    if (s == 200) ok.incrementAndGet(); else fail.incrementAndGet();
                } catch (Exception e) { fail.incrementAndGet(); } finally { done.countDown(); }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "3-thread cancellation race timed out");
        ex.shutdown();

        assertEquals(1, ok.get(), "Exactly 1 of 3 cancellations must succeed. Codes: " + codes);
        assertEquals(2, fail.get(), "Remaining 2 must be rejected. Codes: " + codes);
        LeaveBalance b = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow();
        assertEquals(availBeforeApproval, b.getAvailable(),
                "Balance must be refunded exactly once back to initial available amount. Available: " + b.getAvailable());
        assertEquals(0, b.getUsed(), "Used days must return to 0");
        assertEquals(LeaveStatus.CANCELLED, leaveRequestRepository.findById(lr.getId()).orElseThrow().getStatus());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 11 – Concurrent approval of 2 distinct requests exhausts balance
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void concurrentApprovalOfTwoDistinctRequests_exhaustsBalance_bothApproved_balanceZero()
            throws Exception {
        employee1Balance.setTotal(6); employee1Balance.setAvailable(6); employee1Balance.setUsed(0);
        leaveBalanceRepository.save(employee1Balance);

        // 3-day request A and 3-day request B (non-overlapping)
        LeaveRequest reqA = createPendingRequest(employee1, LocalDate.of(2027,6,2), LocalDate.of(2027,6,4));
        LeaveRequest reqB = createPendingRequest(employee1, LocalDate.of(2027,6,7), LocalDate.of(2027,6,9));

        // 4 threads: 2 race on reqA, 2 race on reqB
        int threads = 4;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());

        Runnable approveA = () -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + reqA.getId() + "/approve")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int s = r.getResponse().getStatus();
                if (s == 200) { ok.incrementAndGet(); outcomes.add("A:OK"); }
                else { fail.incrementAndGet(); outcomes.add("A:FAIL:" + s); }
            } catch (Exception e) { fail.incrementAndGet(); outcomes.add("A:EX"); } finally { done.countDown(); }
        };
        Runnable approveB = () -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + reqB.getId() + "/approve")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                int s = r.getResponse().getStatus();
                if (s == 200) { ok.incrementAndGet(); outcomes.add("B:OK"); }
                else { fail.incrementAndGet(); outcomes.add("B:FAIL:" + s); }
            } catch (Exception e) { fail.incrementAndGet(); outcomes.add("B:EX"); } finally { done.countDown(); }
        };

        ex.submit(approveA); ex.submit(approveA);
        ex.submit(approveB); ex.submit(approveB);

        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS), "Distinct-request approval race timed out");
        ex.shutdown();

        assertEquals(2, ok.get(), "Exactly 2 of 4 approvals must succeed (one per request). Outcomes: " + outcomes);
        assertEquals(2, fail.get(), "Duplicate approvals must fail. Outcomes: " + outcomes);
        assertEquals(LeaveStatus.APPROVED, leaveRequestRepository.findById(reqA.getId()).orElseThrow().getStatus(),
                "Request A must be APPROVED");
        assertEquals(LeaveStatus.APPROVED, leaveRequestRepository.findById(reqB.getId()).orElseThrow().getStatus(),
                "Request B must be APPROVED");
        LeaveBalance b = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow();
        assertEquals(0, b.getAvailable(), "Balance must be exhausted to 0. Available: " + b.getAvailable());
        assertEquals(6, b.getUsed(), "Used must be 6");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TEST 12 – Phantom-read guard: balance invariant under concurrent ops
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    public void phantomReadGuard_concurrentApprovalAndApplication_balanceInvariantNeverViolated()
            throws Exception {
        // 5 days exactly — approval consumes all 5; concurrent 3-day application must fail
        employee1Balance.setTotal(5); employee1Balance.setAvailable(5); employee1Balance.setUsed(0);
        leaveBalanceRepository.save(employee1Balance);

        LeaveRequest bigReq = createPendingRequest(employee1, LocalDate.of(2027,6,14), LocalDate.of(2027,6,18));

        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(2);
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());

        // Thread A: Approve 5-day request (consumes all balance)
        new Thread(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(put("/api/leave-requests/" + bigReq.getId() + "/approve")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)).andReturn();
                outcomes.add("APPROVE:" + r.getResponse().getStatus());
            } catch (Exception e) { outcomes.add("APPROVE:EX"); } finally { done.countDown(); }
        }, "approve-big").start();

        // Thread B: Apply for 3 days next week (would overdraft if lock missing)
        new Thread(() -> {
            try {
                start.await();
                MvcResult r = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization","Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leaveRequestPayload(employee1, LocalDate.of(2027,6,21), LocalDate.of(2027,6,23)))).andReturn();
                outcomes.add("APPLY:" + r.getResponse().getStatus());
            } catch (Exception e) { outcomes.add("APPLY:EX"); } finally { done.countDown(); }
        }, "apply-3day").start();

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Phantom-read guard test timed out");

        // Core invariant: available + used must always == total; no negatives
        LeaveBalance b = leaveBalanceRepository.findById(employee1Balance.getId()).orElseThrow();
        assertTrue(b.getAvailable() >= 0,
                "Balance must never go negative. Available: " + b.getAvailable() + " Outcomes: " + outcomes);
        assertTrue(b.getUsed() <= 5,
                "Used must never exceed total (5). Used: " + b.getUsed() + " Outcomes: " + outcomes);
        assertEquals(5, b.getAvailable() + b.getUsed(),
                "Balance invariant (available + used = total=5) must hold. Outcomes: " + outcomes);
    }
}
