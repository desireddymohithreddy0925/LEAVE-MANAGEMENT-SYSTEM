package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyFilter;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyService;
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
import java.time.LocalDateTime;
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
    private IdempotencyService idempotencyService;

    @Autowired
    private IdempotencyFilter idempotencyFilter;

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

    private Employee noPermEmployee;
    private String noPermToken;

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

        Role emptyRole = roleRepository.findByName("GUEST").orElseGet(() -> roleRepository.save(new Role("GUEST")));

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
        emp1.setLastName("Doe");
        emp1.setEmployeeCode("IDEMP_EMP1_" + System.currentTimeMillis() % 100000);
        emp1.setPhone("1234567890");
        emp1.setStatus("ACTIVE");
        emp1.setSalary(new BigDecimal("60000.00"));
        emp1.setUser(savedUser1);
        employee1 = employeeRepository.save(emp1);
        employee1Token = "Bearer " + jwtUtils.generateTokenFromUsername(user1.getEmail());

        // Employee 1 Leave Balance
        LeaveBalance bal1 = new LeaveBalance();
        bal1.setEmployee(employee1);
        bal1.setLeaveType(annualLeaveType);
        bal1.setYear(2026);
        bal1.setTotal(30);
        bal1.setUsed(0);
        bal1.setAvailable(30);
        leaveBalanceRepository.save(bal1);

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
        emp2.setLastName("Smith");
        emp2.setEmployeeCode("IDEMP_EMP2_" + System.currentTimeMillis() % 100000);
        emp2.setPhone("0987654321");
        emp2.setStatus("ACTIVE");
        emp2.setSalary(new BigDecimal("70000.00"));
        emp2.setUser(savedUser2);
        employee2 = employeeRepository.save(emp2);
        employee2Token = "Bearer " + jwtUtils.generateTokenFromUsername(user2.getEmail());

        LeaveBalance bal2 = new LeaveBalance();
        bal2.setEmployee(employee2);
        bal2.setLeaveType(annualLeaveType);
        bal2.setYear(2026);
        bal2.setTotal(30);
        bal2.setUsed(0);
        bal2.setAvailable(30);
        leaveBalanceRepository.save(bal2);

        // 5. Setup Manager
        User mgrUser = new User();
        mgrUser.setEmail("idemp-mgr-" + UUID.randomUUID() + "@test.com");
        mgrUser.setPassword(passwordEncoder.encode("Pass123!"));
        mgrUser.setActive(true);
        mgrUser.setAccountNonLocked(true);
        mgrUser.setRoles(Set.of(mgrRole));
        User savedMgrUser = userRepository.save(mgrUser);

        Employee mgr = new Employee();
        mgr.setFirstName("Boss");
        mgr.setLastName("Man");
        mgr.setEmployeeCode("IDEMP_MGR_" + System.currentTimeMillis() % 100000);
        mgr.setPhone("5555555555");
        mgr.setStatus("ACTIVE");
        mgr.setSalary(new BigDecimal("90000.00"));
        mgr.setUser(savedMgrUser);
        managerEmployee = employeeRepository.save(mgr);
        managerToken = "Bearer " + jwtUtils.generateTokenFromUsername(mgrUser.getEmail());

        employee1.setManager(managerEmployee);
        employee1 = employeeRepository.save(employee1);

        // 6. Setup No-Perm Employee (Guest)
        User noPermUser = new User();
        noPermUser.setEmail("idemp-noperm-" + UUID.randomUUID() + "@test.com");
        noPermUser.setPassword(passwordEncoder.encode("Pass123!"));
        noPermUser.setActive(true);
        noPermUser.setAccountNonLocked(true);
        noPermUser.setRoles(Set.of(emptyRole));
        User savedNoPermUser = userRepository.save(noPermUser);

        Employee noPerm = new Employee();
        noPerm.setFirstName("No");
        noPerm.setLastName("Perm");
        noPerm.setEmployeeCode("IDEMP_NOP_" + System.currentTimeMillis() % 100000);
        noPerm.setPhone("4444444444");
        noPerm.setStatus("ACTIVE");
        noPerm.setSalary(new BigDecimal("30000.00"));
        noPerm.setUser(savedNoPermUser);
        noPermEmployee = employeeRepository.save(noPerm);
        noPermToken = "Bearer " + jwtUtils.generateTokenFromUsername(noPermUser.getEmail());

        idempotencyFilter.setMaxBodySizeBytes(1048576L); // Reset default 1MB
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
        idempotencyFilter.setMaxBodySizeBytes(1048576L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. RESPONSE REPLAY FULL FIDELITY & REGRESSION TESTS
    // ─────────────────────────────────────────────────────────────────────────

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
    public void testResponseReplay_PreservesStatusBodyContentTypeAndRelevantHeaders() throws Exception {
        String key = "key-full-fidelity-" + UUID.randomUUID();

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 11, 10));
        dto.setEndDate(LocalDate.of(2026, 11, 12));
        dto.setReason("Full fidelity check");

        // 1. First Execution
        MvcResult firstResult = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        int firstStatus = firstResult.getResponse().getStatus();
        String firstBody = firstResult.getResponse().getContentAsString();
        String firstContentType = firstResult.getResponse().getContentType();
        assertNull(firstResult.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));

        // 2. Replay execution with identical request and key
        MvcResult replayedResult = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        // 3. Strict regression comparisons
        assertEquals(firstStatus, replayedResult.getResponse().getStatus(), "HTTP status must match exactly");
        assertEquals(firstBody, replayedResult.getResponse().getContentAsString(), "HTTP body must match exactly");
        assertTrue(replayedResult.getResponse().getContentType().contains("application/json"), "Content-Type must be preserved");
        assertEquals(key, replayedResult.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER));
        assertEquals("true", replayedResult.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));

        // 4. Ensure no duplicate records created in DB
        assertEquals(1, leaveRequestRepository.findByEmployee(employee1).size());
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
        assertEquals(1, leaveRequestRepository.findByEmployee(employee1).size());
    }

    @Test
    public void testIdempotentApprovals_SafeRetryWithoutDoubleApprovalOrError() throws Exception {
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

        // Verify balance deducted only once (3 working days deducted: 30 - 3 = 27)
        LeaveBalance b = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee1, annualLeaveType, 2026).orElseThrow();
        assertEquals(27, b.getAvailable());
        assertEquals(3, b.getUsed());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. FAILURE AND RETRY HANDLING TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testAuthenticationFailure_PermitsRetry_DoesNotLockKey() throws Exception {
        String key = "key-auth-fail-" + UUID.randomUUID();

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 11, 5));
        dto.setEndDate(LocalDate.of(2026, 11, 6));
        dto.setReason("Auth retry test");

        // 1. Submit request with INVALID token -> 401 Unauthorized
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer invalid.expired.token")
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());

        // 2. Client refreshes token and retries with the SAME Idempotency-Key
        MvcResult retryResult = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        assertEquals(key, retryResult.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER));
        assertNull(retryResult.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));

        // Exactly 1 leave request created
        assertEquals(1, leaveRequestRepository.findByEmployee(employee1).size());
    }

    @Test
    public void testAuthorizationFailure_PermitsRetry_DoesNotLockKey() throws Exception {
        String key = "key-authz-fail-" + UUID.randomUUID();

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 11, 9));
        dto.setEndDate(LocalDate.of(2026, 11, 10));
        dto.setReason("Authz retry test");

        // 1. Guest user without LEAVE_CREATE permission -> 403 Forbidden
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", noPermToken)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // 2. Authorized user submits using the SAME key -> succeeds
        MvcResult retryResult = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        assertEquals(key, retryResult.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER));
        assertEquals(1, leaveRequestRepository.findByEmployee(employee1).size());
    }

    @Test
    public void testValidationFailure_PermitsRetry_CanCorrectPayload() throws Exception {
        String key = "key-val-fail-" + UUID.randomUUID();

        // Invalid payload: endDate is before startDate
        LeaveRequestDTO invalidDto = new LeaveRequestDTO();
        invalidDto.setEmployeeId(employee1.getId());
        invalidDto.setLeaveTypeId(annualLeaveType.getId());
        invalidDto.setStartDate(LocalDate.of(2026, 11, 10));
        invalidDto.setEndDate(LocalDate.of(2026, 11, 5)); // BEFORE start date!
        invalidDto.setReason("Invalid dates");

        // 1. Fails with 400 Bad Request
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());

        assertEquals(0, leaveRequestRepository.findByEmployee(employee1).size(), "No leave request created on 400");

        // 2. Client corrects the dates and retries with the SAME Idempotency-Key
        LeaveRequestDTO validDto = new LeaveRequestDTO();
        validDto.setEmployeeId(employee1.getId());
        validDto.setLeaveTypeId(annualLeaveType.getId());
        validDto.setStartDate(LocalDate.of(2026, 11, 10));
        validDto.setEndDate(LocalDate.of(2026, 11, 12)); // Corrected!
        validDto.setReason("Corrected dates");

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto)))
                .andExpect(status().isCreated());

        assertEquals(1, leaveRequestRepository.findByEmployee(employee1).size());
    }

    @Test
    public void testRetryingRequestCannotCreateDuplicateLeaveRequestsOrBalanceChanges() throws Exception {
        String key = "key-prevent-dup-" + UUID.randomUUID();

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 12, 10));
        dto.setEndDate(LocalDate.of(2026, 12, 11));
        dto.setReason("Prevent dup test");

        // Initial submission
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // 3 consecutive retries with identical key
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", employee1Token)
                            .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());
        }

        // Strict assertion: exactly 1 request created
        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(1, requests.size(), "Retries must strictly never create duplicate leave requests");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. INPUT VALIDATION AND RESOURCE LIMITS TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testInvalidKeyFormat_Returns400BadRequest() throws Exception {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 11, 20));
        dto.setEndDate(LocalDate.of(2026, 11, 21));
        dto.setReason("Invalid key test");

        // Key with spaces
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key with spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        // Key with control/newline character
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key\nnewline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        // Key exceeding 255 chars
        String longKey = "a".repeat(256);
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, longKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        // Blank key
        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
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
    public void testOversizedRequestBody_Returns413PayloadTooLarge() throws Exception {
        // Configure max limit to 300 bytes
        idempotencyFilter.setMaxBodySizeBytes(300L);

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 12, 14));
        dto.setEndDate(LocalDate.of(2026, 12, 15));
        dto.setReason("Large text: " + "X".repeat(500)); // Will exceed 300 bytes

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key-oversized-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isPayloadTooLarge());

        // Reset
        idempotencyFilter.setMaxBodySizeBytes(1048576L);
    }

    @Test
    public void testGetRequests_BypassIdempotencyEvenWithHeader() throws Exception {
        mockMvc.perform(get("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "key-get-" + UUID.randomUUID()))
                .andExpect(status().isOk());

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

    // ─────────────────────────────────────────────────────────────────────────
    // 4. EXPIRY AND CLEANUP TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testExpiredKeysCleanup_RemovesExpiredRecordsOnly() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Expired record (expired 2 hours ago)
        IdempotencyKey expired = new IdempotencyKey(
                "key-expired-1", "user@test.com", "/api/test", "POST",
                "hash1", "COMPLETED", now.minusHours(26), now.minusHours(2)
        );
        idempotencyKeyRepository.saveAndFlush(expired);

        // 2. Active record (expires in 24 hours)
        IdempotencyKey active = new IdempotencyKey(
                "key-active-1", "user@test.com", "/api/test", "POST",
                "hash2", "COMPLETED", now, now.plusHours(24)
        );
        idempotencyKeyRepository.saveAndFlush(active);

        assertEquals(2, idempotencyKeyRepository.count());

        // 3. Run cleanup
        int deleted = idempotencyService.cleanupExpiredKeys();
        assertEquals(1, deleted, "Exactly 1 expired record should be deleted");

        // 4. Assert active remains, expired is gone
        assertEquals(1, idempotencyKeyRepository.count());
        assertTrue(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail("key-active-1", "user@test.com").isPresent());
        assertFalse(idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail("key-expired-1", "user@test.com").isPresent());
    }

    @Test
    public void testKeyReuseAfterExpiry_TreatedAsFreshExecution() throws Exception {
        String key = "key-reuse-after-expiry";
        LocalDateTime now = LocalDateTime.now();

        // Create an expired record for employee1
        IdempotencyKey expired = new IdempotencyKey(
                key, employee1.getUser().getEmail(), "/api/leave-requests", "POST",
                "oldhash", "COMPLETED", now.minusHours(48), now.minusHours(24)
        );
        expired.setResponseStatus(201);
        expired.setResponseBody("{\"old\":\"body\"}");
        idempotencyKeyRepository.saveAndFlush(expired);

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee1.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(LocalDate.of(2026, 12, 18));
        dto.setEndDate(LocalDate.of(2026, 12, 19));
        dto.setReason("Reuse after expiry");

        // Submitting with expired key is treated as NEW execution
        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", employee1Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        // Must NOT be marked as replayed
        assertNull(result.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));

        // Stored key must have NEW expiration in the future
        IdempotencyKey updatedKey = idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(key, employee1.getUser().getEmail()).orElseThrow();
        assertTrue(updatedKey.getExpiresAt().isAfter(LocalDateTime.now()), "New key expiration must be in the future");
        assertEquals(1, leaveRequestRepository.findByEmployee(employee1).size());
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
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

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
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(exceptions.isEmpty(), "No unexpected thread exceptions");
        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(1, requests.size(), "Strictly one leave request should be created despite concurrent calls with same key");
        assertEquals(numThreads, successCount.get() + conflictCount.get());
        assertTrue(successCount.get() >= 1, "At least one request must succeed");
    }
}
