package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.service.LeaveBalanceService;
import com.leave_management_system.leave_management_system.service.LeaveRequestService;
import com.leave_management_system.leave_management_system.service.TransactionReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class TransactionReviewIntegrationTest {

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
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TransactionReviewService transactionReviewService;

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private User adminUser;
    private String adminToken;

    private User managerUser;
    private Employee managerEmployee;
    private String managerToken;

    private User employeeUser;
    private Employee regularEmployee;
    private String employeeToken;

    private LeaveType leaveType;
    private LeaveBalance leaveBalance;
    private String uniqueSuffix;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanup();

        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Roles & Permissions setup
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        Permission approvePerm = permissionRepository.findByName("LEAVE_APPROVE").orElseGet(() -> permissionRepository.save(new Permission("LEAVE_APPROVE", "Approve leave")));
        Permission createPerm = permissionRepository.findByName("LEAVE_CREATE").orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CREATE", "Create leave")));
        Permission cancelPerm = permissionRepository.findByName("LEAVE_CANCEL").orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CANCEL", "Cancel leave")));
        Permission viewAllPerm = permissionRepository.findByName("LEAVE_VIEW_ALL").orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW_ALL", "View all leaves")));
        Permission empViewPerm = permissionRepository.findByName("EMPLOYEE_VIEW").orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_VIEW", "View employee")));
        Permission auditViewPerm = permissionRepository.findByName("AUDIT_LOG_VIEW").orElseGet(() -> permissionRepository.save(new Permission("AUDIT_LOG_VIEW", "View audit logs")));
        Permission balanceManagePerm = permissionRepository.findByName("LEAVE_BALANCE_MANAGE").orElseGet(() -> permissionRepository.save(new Permission("LEAVE_BALANCE_MANAGE", "Manage balances")));

        adminRole.getPermissions().addAll(List.of(approvePerm, createPerm, cancelPerm, viewAllPerm, empViewPerm, auditViewPerm, balanceManagePerm));
        roleRepository.save(adminRole);

        managerRole.getPermissions().addAll(List.of(approvePerm, viewAllPerm, empViewPerm));
        roleRepository.save(managerRole);

        empRole.getPermissions().addAll(List.of(createPerm, cancelPerm));
        roleRepository.save(empRole);

        // 2. Setup Admin
        adminUser = new User();
        adminUser.setEmail("tx-admin-" + uniqueSuffix + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        List<SimpleGrantedAuthority> adminAuthorities = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ADMIN_VIEW"),
                new SimpleGrantedAuthority("AUDIT_LOG_VIEW"),
                new SimpleGrantedAuthority("LEAVE_VIEW_ALL"),
                new SimpleGrantedAuthority("LEAVE_BALANCE_MANAGE"),
                new SimpleGrantedAuthority("LEAVE_APPROVE"),
                new SimpleGrantedAuthority("LEAVE_CANCEL"),
                new SimpleGrantedAuthority("LEAVE_CREATE")
        );
        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), adminUser.getPassword(), adminAuthorities);
        adminToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities()));

        // 3. Setup Manager
        managerUser = new User();
        managerUser.setEmail("tx-mgr-" + uniqueSuffix + "@test.com");
        managerUser.setPassword(passwordEncoder.encode("MgrPass123!"));
        managerUser.setActive(true);
        managerUser.getRoles().add(managerRole);
        managerUser = userRepository.save(managerUser);

        managerEmployee = new Employee();
        managerEmployee.setFirstName("Manager");
        managerEmployee.setLastName("Leader");
        managerEmployee.setEmployeeCode("MGR-" + uniqueSuffix);
        managerEmployee.setPhone("555111" + uniqueSuffix.substring(0, 4));
        managerEmployee.setSalary(BigDecimal.valueOf(85000));
        managerEmployee.setStatus("ACTIVE");
        managerEmployee.setUser(managerUser);
        managerEmployee = employeeRepository.save(managerEmployee);

        List<SimpleGrantedAuthority> mgrAuthorities = List.of(
                new SimpleGrantedAuthority("ROLE_MANAGER"),
                new SimpleGrantedAuthority("LEAVE_APPROVE"),
                new SimpleGrantedAuthority("LEAVE_VIEW_ALL"),
                new SimpleGrantedAuthority("EMPLOYEE_VIEW")
        );
        UserDetails mgrDetails = new org.springframework.security.core.userdetails.User(
                managerUser.getEmail(), managerUser.getPassword(), mgrAuthorities);
        managerToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(mgrDetails, null, mgrDetails.getAuthorities()));

        // 4. Setup Regular Employee (reports to Manager)
        employeeUser = new User();
        employeeUser.setEmail("tx-emp-" + uniqueSuffix + "@test.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.getRoles().add(empRole);
        employeeUser = userRepository.save(employeeUser);

        regularEmployee = new Employee();
        regularEmployee.setFirstName("John");
        regularEmployee.setLastName("Doe");
        regularEmployee.setEmployeeCode("EMP-" + uniqueSuffix);
        regularEmployee.setPhone("555222" + uniqueSuffix.substring(0, 4));
        regularEmployee.setSalary(BigDecimal.valueOf(55000));
        regularEmployee.setStatus("ACTIVE");
        regularEmployee.setUser(employeeUser);
        regularEmployee.setManager(managerEmployee);
        regularEmployee = employeeRepository.save(regularEmployee);

        List<SimpleGrantedAuthority> empAuthorities = List.of(
                new SimpleGrantedAuthority("ROLE_EMPLOYEE"),
                new SimpleGrantedAuthority("LEAVE_CREATE"),
                new SimpleGrantedAuthority("LEAVE_CANCEL")
        );
        UserDetails empDetails = new org.springframework.security.core.userdetails.User(
                employeeUser.getEmail(), employeeUser.getPassword(), empAuthorities);
        employeeToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(empDetails, null, empDetails.getAuthorities()));

        // 5. Leave Type & Balance
        leaveType = new LeaveType();
        leaveType.setName("Vacation-" + uniqueSuffix);
        leaveType.setDefaultDays(25);
        leaveType = leaveTypeRepository.save(leaveType);

        leaveBalance = new LeaveBalance();
        leaveBalance.setEmployee(regularEmployee);
        leaveBalance.setLeaveType(leaveType);
        leaveBalance.setYear(LocalDate.now().plusDays(20).getYear());
        leaveBalance.setTotal(25);
        leaveBalance.setAvailable(25);
        leaveBalance.setUsed(0);
        leaveBalance = leaveBalanceRepository.save(leaveBalance);
    }

    @AfterEach
    public void cleanup() {
        transactionReviewRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        auditLogRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        refreshTokenRepository.deleteAll();

        if (regularEmployee != null && regularEmployee.getId() != null) {
            employeeRepository.deleteById(regularEmployee.getId());
            regularEmployee = null;
        }
        if (managerEmployee != null && managerEmployee.getId() != null) {
            employeeRepository.deleteById(managerEmployee.getId());
            managerEmployee = null;
        }
        if (employeeUser != null && employeeUser.getId() != null) {
            userRepository.deleteById(employeeUser.getId());
            employeeUser = null;
        }
        if (managerUser != null && managerUser.getId() != null) {
            userRepository.deleteById(managerUser.getId());
            managerUser = null;
        }
        if (adminUser != null && adminUser.getId() != null) {
            userRepository.deleteById(adminUser.getId());
            adminUser = null;
        }
    }

    @Test
    public void testRoutineLeaveRequestGeneratesLowRiskReviewedTransactionReview() throws Exception {
        LocalDate startDate = getSafeWeekday(LocalDate.now().plusDays(20));
        LocalDate endDate = startDate.plusDays(1); // 2 days

        LeaveRequestDTO requestDTO = new LeaveRequestDTO();
        requestDTO.setEmployeeId(regularEmployee.getId());
        requestDTO.setLeaveTypeId(leaveType.getId());
        requestDTO.setStartDate(startDate);
        requestDTO.setEndDate(endDate);
        requestDTO.setReason("Short routine dental appointment");

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated());

        List<TransactionReview> reviews = transactionReviewRepository.findAll();
        assertFalse(reviews.isEmpty(), "A transaction review should have been automatically recorded");

        TransactionReview review = reviews.stream()
                .filter(r -> r.getTransactionType() == TransactionType.LEAVE_REQUEST)
                .findFirst()
                .orElseThrow();

        assertEquals(TransactionType.LEAVE_REQUEST, review.getTransactionType());
        assertEquals(regularEmployee.getId(), review.getEmployee().getId());
        assertEquals(RiskLevel.LOW, review.getRiskLevel());
        assertEquals(ReviewStatus.REVIEWED, review.getReviewStatus());
        assertEquals("AUTO_CLEARED_ROUTINE", review.getResolution());
        assertNotNull(review.getDetailsJson());
        assertTrue(review.getDetailsJson().contains("Short routine dental appointment"));
    }

    @Test
    public void testExtendedLeaveRequestGeneratesHighRiskFlaggedTransactionReview() throws Exception {
        LocalDate startDate = getSafeWeekday(LocalDate.now().plusDays(20));
        LocalDate endDate = startDate.plusDays(15); // > 10 days

        LeaveRequestDTO requestDTO = new LeaveRequestDTO();
        requestDTO.setEmployeeId(regularEmployee.getId());
        requestDTO.setLeaveTypeId(leaveType.getId());
        requestDTO.setStartDate(startDate);
        requestDTO.setEndDate(endDate);
        requestDTO.setReason("Extended summer sabbatical journey");

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated());

        List<TransactionReview> reviews = transactionReviewRepository.findAll();
        TransactionReview review = reviews.stream()
                .filter(r -> r.getTransactionType() == TransactionType.LEAVE_REQUEST)
                .findFirst()
                .orElseThrow();

        assertEquals(RiskLevel.HIGH, review.getRiskLevel());
        assertEquals(ReviewStatus.FLAGGED, review.getReviewStatus());
        assertTrue(review.getRiskReason().contains("Extended leave request duration"));
        assertNull(review.getResolution());
    }

    @Test
    public void testRetroactiveLeaveRecordGeneratesCriticalRiskFlaggedTransactionReview() {
        LeaveRequest retroactiveRequest = new LeaveRequest();
        retroactiveRequest.setEmployee(regularEmployee);
        retroactiveRequest.setLeaveType(leaveType);
        retroactiveRequest.setStartDate(LocalDate.now().minusDays(10));
        retroactiveRequest.setEndDate(LocalDate.now().minusDays(8));
        retroactiveRequest.setReason("Retroactive emergency leave");
        retroactiveRequest.setStatus(LeaveStatus.PENDING);
        retroactiveRequest = leaveRequestRepository.save(retroactiveRequest);

        TransactionReview review = transactionReviewService.recordLeaveRequest(
                retroactiveRequest, employeeUser, employeeUser.getEmail());

        assertNotNull(review);
        assertEquals(RiskLevel.CRITICAL, review.getRiskLevel());
        assertEquals(ReviewStatus.FLAGGED, review.getReviewStatus());
        assertTrue(review.getRiskReason().contains("Retroactive leave application submitted"));
    }

    @Test
    public void testLeaveApprovalAndCancellationRecordsTransactionReviews() throws Exception {
        LocalDate startDate = getSafeWeekday(LocalDate.now().plusDays(20));
        LocalDate endDate = startDate.plusDays(2);

        LeaveRequest request = new LeaveRequest();
        request.setEmployee(regularEmployee);
        request.setLeaveType(leaveType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setReason("Team conference trip");
        request.setStatus(LeaveStatus.PENDING);
        request = leaveRequestRepository.save(request);

        // 1. Approve leave
        mockMvc.perform(post("/api/leave-requests/" + request.getId() + "/approve")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        List<TransactionReview> reviewsAfterApproval = transactionReviewRepository.findAll();
        assertTrue(reviewsAfterApproval.stream().anyMatch(r -> r.getTransactionType() == TransactionType.LEAVE_APPROVAL));

        // 2. Cancel leave (post-approval cancellation restoring balance)
        mockMvc.perform(post("/api/leave-requests/" + request.getId() + "/cancel")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk());

        List<TransactionReview> reviewsAfterCancel = transactionReviewRepository.findAll();
        TransactionReview cancelReview = reviewsAfterCancel.stream()
                .filter(r -> r.getTransactionType() == TransactionType.LEAVE_CANCELLATION)
                .findFirst()
                .orElseThrow();

        assertEquals(RiskLevel.MEDIUM, cancelReview.getRiskLevel());
        assertEquals(ReviewStatus.PENDING_REVIEW, cancelReview.getReviewStatus());
        assertTrue(cancelReview.getRiskReason().contains("Post-approval leave cancellation"));
    }

    @Test
    public void testManualBalanceAdjustmentRecordsBalanceAdjustmentReview() throws Exception {
        LeaveBalanceRequestDTO updateDTO = new LeaveBalanceRequestDTO();
        updateDTO.setTotal(25);
        updateDTO.setAvailable(15); // Adjusted available from 25 down to 15 (delta = 10 days)

        mockMvc.perform(put("/api/leave-balances/" + leaveBalance.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk());

        List<TransactionReview> reviews = transactionReviewRepository.findAll();
        TransactionReview balanceReview = reviews.stream()
                .filter(r -> r.getTransactionType() == TransactionType.BALANCE_ADJUSTMENT)
                .findFirst()
                .orElseThrow();

        assertEquals(TransactionType.BALANCE_ADJUSTMENT, balanceReview.getTransactionType());
        assertEquals(RiskLevel.HIGH, balanceReview.getRiskLevel());
        assertEquals(ReviewStatus.FLAGGED, balanceReview.getReviewStatus());
        assertTrue(balanceReview.getRiskReason().contains("Significant manual balance adjustment"));
        assertTrue(balanceReview.getDetailsJson().contains("\"oldAvailable\":25"));
        assertTrue(balanceReview.getDetailsJson().contains("\"newAvailable\":15"));
    }

    @Test
    public void testSearchAndFilterTransactionReviews() throws Exception {
        transactionReviewService.recordGenericTransaction(
                TransactionType.DEPARTMENT_TRANSFER, "Employee", regularEmployee.getId(),
                regularEmployee, adminUser, adminUser.getEmail(),
                RiskLevel.HIGH, 70, "Administrative department transfer",
                ReviewStatus.FLAGGED, Map.of("oldDept", "Sales", "newDept", "Engineering"));

        mockMvc.perform(get("/api/transaction-reviews")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("transactionType", "DEPARTMENT_TRANSFER")
                        .param("reviewStatus", "FLAGGED")
                        .param("riskLevel", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].transactionType").value("DEPARTMENT_TRANSFER"))
                .andExpect(jsonPath("$.content[0].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.content[0].reviewStatus").value("FLAGGED"));
    }

    @Test
    public void testGetSummaryMetrics() throws Exception {
        transactionReviewService.recordGenericTransaction(
                TransactionType.BALANCE_ADJUSTMENT, "LeaveBalance", 100L,
                regularEmployee, adminUser, adminUser.getEmail(),
                RiskLevel.HIGH, 65, "High risk adjustment",
                ReviewStatus.FLAGGED, Map.of("delta", 8));

        transactionReviewService.recordGenericTransaction(
                TransactionType.LEAVE_REQUEST, "LeaveRequest", 101L,
                regularEmployee, employeeUser, employeeUser.getEmail(),
                RiskLevel.LOW, 10, "Routine leave",
                ReviewStatus.REVIEWED, Map.of("days", 1));

        mockMvc.perform(get("/api/transaction-reviews/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totalHighRisk", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalReviewed", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalFlagged", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.pendingHighRiskCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.reviewCompletionRate", greaterThanOrEqualTo(0.0)));
    }

    @Test
    public void testAssignReviewerTransitionsStatusToUnderReview() throws Exception {
        TransactionReview review = transactionReviewService.recordGenericTransaction(
                TransactionType.BALANCE_ADJUSTMENT, "LeaveBalance", 200L,
                regularEmployee, adminUser, adminUser.getEmail(),
                RiskLevel.HIGH, 70, "Needs inspection",
                ReviewStatus.PENDING_REVIEW, null);

        AssignReviewerDTO assignDTO = new AssignReviewerDTO(adminUser.getId(), adminUser.getEmail());

        mockMvc.perform(post("/api/transaction-reviews/" + review.getId() + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(review.getId()))
                .andExpect(jsonPath("$.reviewStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reviewerEmail").value(adminUser.getEmail()))
                .andExpect(jsonPath("$.reviewerId").value(adminUser.getId()));
    }

    @Test
    public void testSubmitReviewDecisionTransitionsToReviewed() throws Exception {
        TransactionReview review = transactionReviewService.recordGenericTransaction(
                TransactionType.LEAVE_REQUEST, "LeaveRequest", 300L,
                regularEmployee, employeeUser, employeeUser.getEmail(),
                RiskLevel.MEDIUM, 40, "Needs audit",
                ReviewStatus.UNDER_REVIEW, null);

        ReviewDecisionDTO decisionDTO = new ReviewDecisionDTO(
                ReviewStatus.REVIEWED, "Verified supporting medical documents", "APPROVED_VALIDATED");

        mockMvc.perform(post("/api/transaction-reviews/" + review.getId() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decisionDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(review.getId()))
                .andExpect(jsonPath("$.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.reviewNotes").value("Verified supporting medical documents"))
                .andExpect(jsonPath("$.resolution").value("APPROVED_VALIDATED"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
    }

    @Test
    public void testFlagTransactionByAuditor() throws Exception {
        TransactionReview review = transactionReviewService.recordGenericTransaction(
                TransactionType.LEAVE_REQUEST, "LeaveRequest", 400L,
                regularEmployee, employeeUser, employeeUser.getEmail(),
                RiskLevel.LOW, 10, "Routine",
                ReviewStatus.REVIEWED, null);

        FlagTransactionDTO flagDTO = new FlagTransactionDTO(
                "Unusual consecutive leave cluster detected", "Escalated for VP review");

        mockMvc.perform(post("/api/transaction-reviews/" + review.getId() + "/flag")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(flagDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(review.getId()))
                .andExpect(jsonPath("$.reviewStatus").value("FLAGGED"))
                .andExpect(jsonPath("$.riskReason", containsString("Auditor Flag: Unusual consecutive leave cluster detected")))
                .andExpect(jsonPath("$.reviewNotes").value("Escalated for VP review"));
    }

    @Test
    public void testResolveFlaggedReviewByAuditor() throws Exception {
        TransactionReview review = transactionReviewService.recordGenericTransaction(
                TransactionType.BALANCE_ADJUSTMENT, "LeaveBalance", 500L,
                regularEmployee, adminUser, adminUser.getEmail(),
                RiskLevel.CRITICAL, 85, "Zero balance override",
                ReviewStatus.FLAGGED, null);

        ResolveReviewDTO resolveDTO = new ResolveReviewDTO(
                "RESOLVED_POLICY_EXCEPTION", "Approved by Executive HR Director under compassionate grounds");

        mockMvc.perform(post("/api/transaction-reviews/" + review.getId() + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(review.getId()))
                .andExpect(jsonPath("$.reviewStatus").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("RESOLVED_POLICY_EXCEPTION"))
                .andExpect(jsonPath("$.reviewNotes").value("Approved by Executive HR Director under compassionate grounds"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
    }

    @Test
    public void testSelfServiceMyTransactionReviews() throws Exception {
        transactionReviewService.recordGenericTransaction(
                TransactionType.LEAVE_REQUEST, "LeaveRequest", 601L,
                regularEmployee, employeeUser, employeeUser.getEmail(),
                RiskLevel.LOW, 10, "Routine",
                ReviewStatus.REVIEWED, null);

        mockMvc.perform(get("/api/transaction-reviews/me")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].employeeId").value(regularEmployee.getId()));
    }

    @Test
    public void testRbacProtectionForTransactionReviews() throws Exception {
        // 1. Unauthenticated -> 401
        mockMvc.perform(get("/api/transaction-reviews"))
                .andExpect(status().isUnauthorized());

        // 2. Regular employee accessing administrative search -> 403 Forbidden
        mockMvc.perform(get("/api/transaction-reviews")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        // 3. Regular employee attempting review submission -> 403 Forbidden
        ReviewDecisionDTO decisionDTO = new ReviewDecisionDTO(ReviewStatus.REVIEWED, "Unauthorized attempt", "APPROVED");
        mockMvc.perform(post("/api/transaction-reviews/999/review")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decisionDTO)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testBridgeEndpointInAuditLogs() throws Exception {
        transactionReviewService.recordGenericTransaction(
                TransactionType.LEAVE_APPROVAL, "LeaveRequest", 700L,
                regularEmployee, managerUser, managerUser.getEmail(),
                RiskLevel.LOW, 15, "Standard approval",
                ReviewStatus.REVIEWED, null);

        mockMvc.perform(get("/api/audit-logs/transaction-reviews")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].transactionType").isNotEmpty());
    }

    private LocalDate getSafeWeekday(LocalDate date) {
        while (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
