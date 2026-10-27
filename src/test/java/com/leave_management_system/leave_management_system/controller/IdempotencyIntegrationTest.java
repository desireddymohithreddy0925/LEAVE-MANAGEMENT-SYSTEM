package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyFilter;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class IdempotencyIntegrationTest {

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
    private LeaveRequestSignoffRepository leaveRequestSignoffRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

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

    private Employee employee1;
    private String employee1Token;

    private Employee employee2;
    private String employee2Token;

    private Employee managerEmployee;
    private String managerToken;

    private LeaveType annualLeaveType;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        cleanup();

        // 1. Setup Permissions
        List<String> perms = List.of("LEAVE_CREATE", "LEAVE_CANCEL", "LEAVE_APPROVE", "LEAVE_VIEW_ALL");
        Set<Permission> permissions = new HashSet<>();
        for (String permName : perms) {
            Permission p = permissionRepository.findByName(permName)
                    .orElseGet(() -> permissionRepository.save(new Permission(permName, permName + " permission")));
            permissions.add(p);
        }

        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        empRole.getPermissions().addAll(permissions);
        roleRepository.save(empRole);

        Role mgrRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        mgrRole.getPermissions().addAll(permissions);
        roleRepository.save(mgrRole);

        // 2. Setup Leave Types
        LeaveType lt = new LeaveType();
        lt.setName("Idempotency Annual " + UUID.randomUUID().toString().substring(0, 5));
        lt.setDefaultDays(30);
        lt.setActive(true);
        annualLeaveType = leaveTypeRepository.save(lt);

        // 3. Setup Employee 1
        User user1 = new User();
        user1.setEmail("idemp-emp1-" + UUID.randomUUID() + "@test.com");
        user1.setPassword(passwordEncoder.encode("Pass123!"));
        user1.setActive(true);
        user1.setAccountNonLocked(true);
        user1.setRoles(Set.of(empRole));
        User savedUser1 = userRepository.save(user1);

        Employee emp1 = new Employee();
        emp1.setFirstName("John");
        emp1.setLastName("Idemp");
        emp1.setEmployeeCode("IDEMP_1_" + System.currentTimeMillis() % 10000);
        emp1.setPhone("9876543201");
        emp1.setStatus("ACTIVE");
        emp1.setSalary(new BigDecimal("70000.00"));
        emp1.setUser(savedUser1);
        employee1 = employeeRepository.save(emp1);

        LeaveBalance lb1 = new LeaveBalance();
        lb1.setEmployee(employee1);
        lb1.setLeaveType(annualLeaveType);
        lb1.setYear(2026);
        lb1.setTotal(30);
        lb1.setUsed(0);
        lb1.setAvailable(30);
        leaveBalanceRepository.save(lb1);

        employee1Token = "Bearer " + jwtUtils.generateTokenFromUsername(savedUser1.getEmail());

        // 4. Setup Employee 2
        User user2 = new User();
        user2.setEmail("idemp-emp2-" + UUID.randomUUID() + "@test.com");
        user2.setPassword(passwordEncoder.encode("Pass123!"));
        user2.setActive(true);
        user2.setAccountNonLocked(true);
        user2.setRoles(Set.of(empRole));
        User savedUser2 = userRepository.save(user2);

        Employee emp2 = new Employee();
        emp2.setFirstName("Jane");
        emp2.setLastName("Idemp");
        emp2.setEmployeeCode("IDEMP_2_" + (System.currentTimeMillis() + 1) % 10000);
        emp2.setPhone("9876543202");
        emp2.setStatus("ACTIVE");
        emp2.setSalary(new BigDecimal("75000.00"));
        emp2.setUser(savedUser2);
        employee2 = employeeRepository.save(emp2);

        LeaveBalance lb2 = new LeaveBalance();
        lb2.setEmployee(employee2);
        lb2.setLeaveType(annualLeaveType);
        lb2.setYear(2026);
        lb2.setTotal(30);
        lb2.setUsed(0);
        lb2.setAvailable(30);
        leaveBalanceRepository.save(lb2);

        employee2Token = "Bearer " + jwtUtils.generateTokenFromUsername(savedUser2.getEmail());

        // 5. Setup Manager
        User mgrUser = new User();
        mgrUser.setEmail("idemp-mgr-" + UUID.randomUUID() + "@test.com");
        mgrUser.setPassword(passwordEncoder.encode("Pass123!"));
        mgrUser.setActive(true);
        mgrUser.setAccountNonLocked(true);
        mgrUser.setRoles(Set.of(mgrRole));
        User savedMgrUser = userRepository.save(mgrUser);

        Employee mgrEmp = new Employee();
        mgrEmp.setFirstName("Manager");
        mgrEmp.setLastName("Idemp");
        mgrEmp.setEmployeeCode("IDEMP_MGR_" + (System.currentTimeMillis() + 2) % 10000);
        mgrEmp.setPhone("9876543203");
        mgrEmp.setStatus("ACTIVE");
        mgrEmp.setSalary(new BigDecimal("90000.00"));
        mgrEmp.setUser(savedMgrUser);
        managerEmployee = employeeRepository.save(mgrEmp);

        employee1.setManager(managerEmployee);
        employee1 = employeeRepository.save(employee1);

        managerToken = "Bearer " + jwtUtils.generateTokenFromUsername(savedMgrUser.getEmail());
    }

    @AfterEach
    public void cleanup() {
        idempotencyKeyRepository.deleteAll();
        leaveRequestSignoffRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        employeeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        transactionReviewRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testFirstRequestWithIdempotencyKey_SucceedsAndRecordsKey() throws Exception {
        String key = "key-first-" + UUID.randomUUID();

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 11, 2));
        dto.setEndDate(LocalDate.of(2026, 11, 4));
        dto.setReason("Vacation");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        assertEquals(key, result.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER));
        assertNull(result.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));

        // Verify key stored in database
        Optional<IdempotencyKey> keyOpt = idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(key, employee1.getUser().getEmail());
        assertTrue(keyOpt.isPresent());
        assertEquals("COMPLETED", keyOpt.get().getStatus());
        assertEquals(201, keyOpt.get().getResponseStatus());
        assertNotNull(keyOpt.get().getResponseBody());
        assertTrue(keyOpt.get().getResponseBody().contains("Vacation"));

        // Verify leave request in DB
        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(1, requests.size());
    }

    @Test
    public void testReplayingIdenticalRequest_ReturnsCachedResponseWithReplayedHeader() throws Exception {
        String key = "key-replay-" + UUID.randomUUID();

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 11, 10));
        dto.setEndDate(LocalDate.of(2026, 11, 12));
        dto.setReason("Medical checkup");

        // First execution
        MvcResult firstResult = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        assertNull(firstResult.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));
        LeaveResponseDTO firstResponse = objectMapper.readValue(firstResult.getResponse().getContentAsString(), LeaveResponseDTO.class);
        assertNotNull(firstResponse.getId());

        // Replay identical request with same key
        MvcResult replayedResult = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        assertEquals(key, replayedResult.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER));
        assertEquals("true", replayedResult.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));

        LeaveResponseDTO replayedResponse = objectMapper.readValue(replayedResult.getResponse().getContentAsString(), LeaveResponseDTO.class);
        assertEquals(firstResponse.getId(), replayedResponse.getId());
        assertEquals(firstResponse.getReason(), replayedResponse.getReason());

        // Verify only 1 leave request exists in DB (not duplicated, no overlapping leave error!)
        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(1, requests.size());
    }

    @Test
    public void testReplayingWithDifferentPayload_Returns409Conflict() throws Exception {
        String key = "key-mismatch-" + UUID.randomUUID();

        LeaveRequestDTO dto1 = new LeaveRequestDTO();
        dto1.setEmployeeId(employee1.getId());
        dto1.setLeaveTypeId(annualLeaveType.getId());
        dto1.setStartDate(LocalDate.of(2026, 11, 16));
        dto1.setEndDate(LocalDate.of(2026, 11, 18));
        dto1.setReason("Trip");

        // First execution
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isCreated());

        // Second execution with SAME key but DIFFERENT payload
        LeaveRequestDTO dto2 = new LeaveRequestDTO();
        dto2.setEmployeeId(employee1.getId());
        dto2.setLeaveTypeId(annualLeaveType.getId());
        dto2.setStartDate(LocalDate.of(2026, 11, 23));
        dto2.setEndDate(LocalDate.of(2026, 11, 25));
        dto2.setReason("Completely different reason");

        MvcResult mismatchResult = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isConflict())
                .andReturn();

        String body = mismatchResult.getResponse().getContentAsString();
        assertTrue(body.contains("Idempotency key payload mismatch"));

        // Verify only 1 leave request exists in DB
        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(1, requests.size());
    }

    @Test
    public void testIdempotentApprovals_SafeRetryWithoutDoubleApprovalOrError() throws Exception {
        // Create a leave request
        LeaveRequest req = new LeaveRequest();
        req.setEmployee(employee1);
        req.setLeaveType(annualLeaveType);
        req.setStartDate(LocalDate.of(2026, 12, 1));
        req.setEndDate(LocalDate.of(2026, 12, 3));
        req.setStatus(LeaveStatus.PENDING);
        req.setReason("Need rest");
        req.setRequiresDualApproval(false);
        req = leaveRequestRepository.save(req);

        String approveKey = "key-approve-" + UUID.randomUUID();

        // First approval
        MvcResult firstApproval = mockMvc.perform(put("/api/leave-requests/" + req.getId() + "/approve")
                        .header("Authorization", managerToken)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, approveKey))
                .andExpect(status().isOk())
                .andReturn();

        assertNull(firstApproval.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));

        // Second approval retry with same key
        MvcResult secondApproval = mockMvc.perform(put("/api/leave-requests/" + req.getId() + "/approve")
                        .header("Authorization", managerToken)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, approveKey))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("true", secondApproval.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));
        assertEquals(approveKey, secondApproval.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER));
    }

    @Test
    public void testRequestsWithoutIdempotencyKey_BypassIdempotency() throws Exception {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 12, 8));
        dto.setEndDate(LocalDate.of(2026, 12, 9));
        dto.setReason("No idempotency header test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        assertNull(result.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER));
        assertNull(result.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));

        // DB idempotency table should have 0 entries
        assertEquals(0, idempotencyKeyRepository.count());
    }

    @Test
    public void testGetRequests_BypassIdempotencyEvenWithHeader() throws Exception {
        mockMvc.perform(get("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key-get-" + UUID.randomUUID()))
                .andExpect(status().isOk());

        // Idempotency table should NOT store GET requests
        assertEquals(0, idempotencyKeyRepository.count());
    }

    @Test
    public void testDifferentUsersWithSameKey_IsolatedAndIndependent() throws Exception {
        String commonKey = "shared-uuid-key";

        LeaveRequestDTO dto1 = new LeaveRequestDTO();
        dto1.setEmployeeId(employee1.getId());
        dto1.setLeaveTypeId(annualLeaveType.getId());
        dto1.setStartDate(LocalDate.of(2026, 12, 15));
        dto1.setEndDate(LocalDate.of(2026, 12, 16));
        dto1.setReason("User 1 leave");

        LeaveRequestDTO dto2 = new LeaveRequestDTO();
        dto2.setEmployeeId(employee2.getId());
        dto2.setLeaveTypeId(annualLeaveType.getId());
        dto2.setStartDate(LocalDate.of(2026, 12, 15));
        dto2.setEndDate(LocalDate.of(2026, 12, 16));
        dto2.setReason("User 2 leave");

        // User 1 uses key
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, commonKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isCreated());

        // User 2 uses the exact same key without interference
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee2Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, commonKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isCreated());

        // Both keys are recorded separately for user1 and user2
        assertTrue(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(commonKey, employee1.getUser().getEmail()).isPresent());
        assertTrue(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(commonKey, employee2.getUser().getEmail()).isPresent());
        assertEquals(2, idempotencyKeyRepository.count());
    }

    @Test
    public void testConcurrentRequestsWithSameIdempotencyKey_PreventsDoubleExecution() throws Exception {
        String key = "key-concurrent-" + UUID.randomUUID();

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 12, 22));
        dto.setEndDate(LocalDate.of(2026, 12, 24));
        dto.setReason("Concurrent stress test");

        int numThreads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                    .header("Authorization", employee1Token)
                                    .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    statusCodes.add(status);
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 leave request must have been created in the database!
        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(1, requests.size(), "Strictly one leave request should be created despite concurrent calls with same key");

        // Successes (first execution or replay) + Conflicts (in progress) must equal total threads
        assertEquals(numThreads, successCount.get() + conflictCount.get());
        assertTrue(successCount.get() >= 1, "At least one request must succeed");
    }
}
