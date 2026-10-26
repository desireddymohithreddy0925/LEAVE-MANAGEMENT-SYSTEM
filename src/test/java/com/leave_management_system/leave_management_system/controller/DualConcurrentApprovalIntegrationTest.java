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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class DualConcurrentApprovalIntegrationTest {

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

    private User managerUser;
    private String managerToken;
    private Employee managerEmployee;

    private User hrUser;
    private String hrToken;

    private User empUser;
    private String empToken;
    private Employee testEmployee;

    private LeaveType testLeaveType;
    private LeaveBalance testBalance;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        cleanup();

        // 1. Permissions setup
        List<String> approvalPerms = List.of(
                "LEAVE_APPROVE", "LEAVE_REJECT", "LEAVE_CANCEL", "LEAVE_VIEW_ALL",
                "LEAVE_BALANCE_MANAGE", "LEAVE_CREATE", "EMPLOYEE_VIEW"
        );
        Set<Permission> permissions = new HashSet<>();
        for (String permName : approvalPerms) {
            Permission p = permissionRepository.findByName(permName)
                    .orElseGet(() -> permissionRepository.save(new Permission(permName, permName + " permission")));
            permissions.add(p);
        }

        // 2. Setup ADMIN
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        adminRole.getPermissions().addAll(permissions);
        roleRepository.save(adminRole);

        adminUser = new User();
        adminUser.setEmail("admin-dual-" + UUID.randomUUID() + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.setRoles(Set.of(adminRole));
        adminUser = userRepository.save(adminUser);
        adminToken = jwtUtils.generateTokenFromUsername(adminUser.getEmail());

        // 3. Setup MANAGER
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        managerRole.getPermissions().addAll(permissions);
        roleRepository.save(managerRole);

        managerUser = new User();
        managerUser.setEmail("mgr-dual-" + UUID.randomUUID() + "@test.com");
        managerUser.setPassword(passwordEncoder.encode("MgrPass123!"));
        managerUser.setActive(true);
        managerUser.setAccountNonLocked(true);
        managerUser.setRoles(Set.of(managerRole));
        managerUser = userRepository.save(managerUser);
        managerToken = jwtUtils.generateTokenFromUsername(managerUser.getEmail());

        managerEmployee = new Employee();
        managerEmployee.setFirstName("Mark");
        managerEmployee.setLastName("Manager");
        managerEmployee.setEmployeeCode("MGR_" + System.currentTimeMillis() % 10000);
        managerEmployee.setPhone("9123456780");
        managerEmployee.setStatus("ACTIVE");
        managerEmployee.setSalary(new BigDecimal("90000.00"));
        managerEmployee.setUser(managerUser);
        managerEmployee = employeeRepository.save(managerEmployee);

        // 4. Setup HR
        Role hrRole = roleRepository.findByName("HR").orElseGet(() -> roleRepository.save(new Role("HR")));
        hrRole.getPermissions().addAll(permissions);
        roleRepository.save(hrRole);

        hrUser = new User();
        hrUser.setEmail("hr-dual-" + UUID.randomUUID() + "@test.com");
        hrUser.setPassword(passwordEncoder.encode("HrPass123!"));
        hrUser.setActive(true);
        hrUser.setAccountNonLocked(true);
        hrUser.setRoles(Set.of(hrRole));
        hrUser = userRepository.save(hrUser);
        hrToken = jwtUtils.generateTokenFromUsername(hrUser.getEmail());

        // 5. Setup EMPLOYEE
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        Permission cancelPerm = permissionRepository.findByName("LEAVE_CANCEL").orElseThrow();
        Permission createPerm = permissionRepository.findByName("LEAVE_CREATE").orElseThrow();
        empRole.getPermissions().addAll(Set.of(cancelPerm, createPerm));
        roleRepository.save(empRole);

        empUser = new User();
        empUser.setEmail("emp-dual-" + UUID.randomUUID() + "@test.com");
        empUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        empUser.setActive(true);
        empUser.setAccountNonLocked(true);
        empUser.setRoles(Set.of(empRole));
        empUser = userRepository.save(empUser);
        empToken = jwtUtils.generateTokenFromUsername(empUser.getEmail());

        testEmployee = new Employee();
        testEmployee.setFirstName("Alice");
        testEmployee.setLastName("Applicant");
        testEmployee.setEmployeeCode("EMP_" + System.currentTimeMillis() % 10000);
        testEmployee.setPhone("9876543211");
        testEmployee.setStatus("ACTIVE");
        testEmployee.setSalary(new BigDecimal("60000.00"));
        testEmployee.setUser(empUser);
        testEmployee.setManager(managerEmployee);
        testEmployee = employeeRepository.save(testEmployee);

        // 6. Setup LeaveType & LeaveBalance
        LeaveType lt = new LeaveType();
        lt.setName("Annual Vacation Dual " + UUID.randomUUID().toString().substring(0, 5));
        lt.setDefaultDays(20);
        lt.setActive(true);
        testLeaveType = leaveTypeRepository.save(lt);

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
        leaveRequestSignoffRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        employeeRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        userRepository.deleteAll();
    }

    private LeaveRequest createDualApprovalPendingRequest(LocalDate start, LocalDate end) {
        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(testEmployee);
        lr.setLeaveType(testLeaveType);
        lr.setStartDate(start);
        lr.setEndDate(end);
        lr.setStatus(LeaveStatus.PENDING);
        lr.setRequiresDualApproval(true);
        lr.setManagerApprovalStatus("PENDING");
        lr.setHrApprovalStatus("PENDING");
        lr.setReason("Testing dual parallel concurrent approvals");
        return leaveRequestRepository.save(lr);
    }

    @Test
    public void testDualApproval_managerThenHr_transitionsToApprovedAndDeductsBalance() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 3); // Tuesday
        LocalDate end = LocalDate.of(2026, 11, 5);   // Thursday (3 days)
        LeaveRequest lr = createDualApprovalPendingRequest(start, end);

        // Step 1: Manager approves first
        MvcResult mgrResult = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode mgrJson = objectMapper.readTree(mgrResult.getResponse().getContentAsString());
        assertEquals("PARTIALLY_APPROVED", mgrJson.get("status").asText());
        assertTrue(mgrJson.get("requiresDualApproval").asBoolean());
        assertEquals("APPROVED", mgrJson.get("managerApprovalStatus").asText());
        assertEquals("PENDING", mgrJson.get("hrApprovalStatus").asText());

        // Balance should NOT be deducted yet
        LeaveBalance balAfterFirstSignoff = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(20, balAfterFirstSignoff.getAvailable(), "Balance must remain untouched after first signoff");
        assertEquals(0, balAfterFirstSignoff.getUsed());

        // Verify signoffs endpoint
        MvcResult signoffRes1 = mockMvc.perform(get("/api/leave-requests/" + lr.getId() + "/signoffs")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode signoffs1 = objectMapper.readTree(signoffRes1.getResponse().getContentAsString());
        assertEquals(1, signoffs1.size());
        assertEquals("MANAGER", signoffs1.get(0).get("approverRole").asText());
        assertEquals("APPROVED", signoffs1.get(0).get("status").asText());

        // Step 2: HR approves second
        MvcResult hrResult = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode hrJson = objectMapper.readTree(hrResult.getResponse().getContentAsString());
        assertEquals("APPROVED", hrJson.get("status").asText());
        assertEquals("APPROVED", hrJson.get("managerApprovalStatus").asText());
        assertEquals("APPROVED", hrJson.get("hrApprovalStatus").asText());

        // Balance SHOULD now be deducted by 3 days
        LeaveBalance balAfterSecondSignoff = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(17, balAfterSecondSignoff.getAvailable(), "Balance must be decremented by 3 days after both signoffs");
        assertEquals(3, balAfterSecondSignoff.getUsed());

        // Verify signoffs endpoint has 2 records
        MvcResult signoffRes2 = mockMvc.perform(get("/api/leave-requests/" + lr.getId() + "/signoffs")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode signoffs2 = objectMapper.readTree(signoffRes2.getResponse().getContentAsString());
        assertEquals(2, signoffs2.size());
    }

    @Test
    public void testDualApproval_hrThenManager_transitionsToApprovedAndDeductsBalance() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 10); // Tuesday
        LocalDate end = LocalDate.of(2026, 11, 12);   // Thursday (3 days)
        LeaveRequest lr = createDualApprovalPendingRequest(start, end);

        // Step 1: HR approves first
        MvcResult hrResult = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode hrJson = objectMapper.readTree(hrResult.getResponse().getContentAsString());
        assertEquals("PARTIALLY_APPROVED", hrJson.get("status").asText());
        assertEquals("PENDING", hrJson.get("managerApprovalStatus").asText());
        assertEquals("APPROVED", hrJson.get("hrApprovalStatus").asText());

        LeaveBalance bal1 = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(20, bal1.getAvailable(), "Balance untouched after HR signoff");

        // Step 2: Manager approves second
        MvcResult mgrResult = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode mgrJson = objectMapper.readTree(mgrResult.getResponse().getContentAsString());
        assertEquals("APPROVED", mgrJson.get("status").asText());
        assertEquals("APPROVED", mgrJson.get("managerApprovalStatus").asText());
        assertEquals("APPROVED", mgrJson.get("hrApprovalStatus").asText());

        LeaveBalance bal2 = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(17, bal2.getAvailable(), "Balance decremented after second signoff");
        assertEquals(3, bal2.getUsed());
    }

    @Test
    public void testDualApproval_adminDirectOverride_immediatelyApprovedAndDeducted() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 17); // Tuesday
        LocalDate end = LocalDate.of(2026, 11, 19);   // Thursday (3 days)
        LeaveRequest lr = createDualApprovalPendingRequest(start, end);

        MvcResult adminResult = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode adminJson = objectMapper.readTree(adminResult.getResponse().getContentAsString());
        assertEquals("APPROVED", adminJson.get("status").asText(), "Admin should directly approve even if dual approval required");
        assertEquals("APPROVED", adminJson.get("managerApprovalStatus").asText());
        assertEquals("APPROVED", adminJson.get("hrApprovalStatus").asText());

        LeaveBalance bal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(17, bal.getAvailable());
        assertEquals(3, bal.getUsed());

        MvcResult signoffRes = mockMvc.perform(get("/api/leave-requests/" + lr.getId() + "/signoffs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode signoffs = objectMapper.readTree(signoffRes.getResponse().getContentAsString());
        assertEquals(1, signoffs.size());
        assertEquals("ADMIN", signoffs.get(0).get("approverRole").asText());
    }

    @Test
    public void testDualApproval_concurrentSimultaneousApprovals_bothThreadsSucceed_statusApproved_balanceDeductedOnce() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 24); // Tuesday
        LocalDate end = LocalDate.of(2026, 11, 26);   // Thursday (3 days)
        LeaveRequest lr = createDualApprovalPendingRequest(start, end);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        List<String> returnedStatuses = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: Manager
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andReturn();
                statusCodes.add(res.getResponse().getStatus());
                if (res.getResponse().getStatus() == 200) {
                    successCount.incrementAndGet();
                    JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
                    returnedStatuses.add(node.get("status").asText());
                }
            } catch (Exception e) {
                // log
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: HR
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andReturn();
                statusCodes.add(res.getResponse().getStatus());
                if (res.getResponse().getStatus() == 200) {
                    successCount.incrementAndGet();
                    JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
                    returnedStatuses.add(node.get("status").asText());
                }
            } catch (Exception e) {
                // log
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent parallel sign-offs timed out");
        executor.shutdown();

        assertEquals(2, successCount.get(), "Both Manager and HR sign-offs must succeed with HTTP 200 OK. Codes: " + statusCodes);
        assertTrue(returnedStatuses.contains("APPROVED"), "At least one thread must observe the final APPROVED state");

        // Verify DB final state
        LeaveRequest finalReq = leaveRequestRepository.findById(lr.getId()).orElseThrow();
        assertEquals(LeaveStatus.APPROVED, finalReq.getStatus());
        assertEquals("APPROVED", finalReq.getManagerApprovalStatus());
        assertEquals("APPROVED", finalReq.getHrApprovalStatus());

        // Balance must be deducted exactly once (3 days)
        LeaveBalance finalBal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(17, finalBal.getAvailable(), "Balance must be deducted exactly 3 days once");
        assertEquals(3, finalBal.getUsed());

        // Exactly 2 signoffs recorded
        List<LeaveRequestSignoff> signoffs = leaveRequestSignoffRepository.findByLeaveRequestId(lr.getId());
        assertEquals(2, signoffs.size(), "Two signoffs must be persisted in database");
    }

    @Test
    public void testDualApproval_duplicateSignoff_sameRole_returns400BadRequest() throws Exception {
        LocalDate start = LocalDate.of(2026, 12, 1); // Tuesday
        LocalDate end = LocalDate.of(2026, 12, 3);   // Thursday (3 days)
        LeaveRequest lr = createDualApprovalPendingRequest(start, end);

        // First sign-off by manager -> 200 OK
        mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Second sign-off by manager -> 400 Bad Request
        MvcResult dupResult = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(dupResult.getResponse().getContentAsString().contains("Manager has already approved this leave request"));
    }

    @Test
    public void testDualApproval_rejectAfterManagerPartialApproval_rejectsAndLeavesBalanceUntouched() throws Exception {
        LocalDate start = LocalDate.of(2026, 12, 8); // Tuesday
        LocalDate end = LocalDate.of(2026, 12, 10);  // Thursday (3 days)
        LeaveRequest lr = createDualApprovalPendingRequest(start, end);

        // Manager approves -> PARTIALLY_APPROVED
        mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // HR rejects
        MvcResult rejResult = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/reject?reason=Budget+constraints")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rejJson = objectMapper.readTree(rejResult.getResponse().getContentAsString());
        assertEquals("REJECTED", rejJson.get("status").asText());
        assertEquals("REJECTED", rejJson.get("hrApprovalStatus").asText());

        // Balance remains untouched
        LeaveBalance bal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(20, bal.getAvailable());
        assertEquals(0, bal.getUsed());
    }

    @Test
    public void testDualApproval_cancelAfterPartialApproval_cancelsCleanlyWithoutModifyingBalance() throws Exception {
        LocalDate start = LocalDate.of(2026, 12, 15); // Tuesday
        LocalDate end = LocalDate.of(2026, 12, 17);   // Thursday (3 days)
        LeaveRequest lr = createDualApprovalPendingRequest(start, end);

        // Manager approves -> PARTIALLY_APPROVED
        mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Employee cancels own request
        MvcResult cancelResult = mockMvc.perform(put("/api/leave-requests/" + lr.getId() + "/cancel")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode cancelJson = objectMapper.readTree(cancelResult.getResponse().getContentAsString());
        assertEquals("CANCELLED", cancelJson.get("status").asText());

        LeaveBalance bal = leaveBalanceRepository.findById(testBalance.getId()).orElseThrow();
        assertEquals(20, bal.getAvailable());
        assertEquals(0, bal.getUsed());
    }

    @Test
    public void testDualApproval_overlapDetection_blocksOverlappingRequestWhenPartiallyApproved() throws Exception {
        LocalDate start1 = LocalDate.of(2026, 12, 22); // Tuesday
        LocalDate end1 = LocalDate.of(2026, 12, 24);   // Thursday
        LeaveRequest lr1 = createDualApprovalPendingRequest(start1, end1);

        // Manager approves -> PARTIALLY_APPROVED
        mockMvc.perform(put("/api/leave-requests/" + lr1.getId() + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Try creating an overlapping request (Dec 23 - Dec 25)
        LeaveRequestDTO overlapDto = new LeaveRequestDTO();
        overlapDto.setEmployeeId(testEmployee.getId());
        overlapDto.setLeaveTypeId(testLeaveType.getId());
        overlapDto.setStartDate(LocalDate.of(2026, 12, 23));
        overlapDto.setEndDate(LocalDate.of(2026, 12, 25));
        overlapDto.setReason("Overlapping with partially approved request");

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapDto)))
                .andExpect(status().isBadRequest());
    }
}
