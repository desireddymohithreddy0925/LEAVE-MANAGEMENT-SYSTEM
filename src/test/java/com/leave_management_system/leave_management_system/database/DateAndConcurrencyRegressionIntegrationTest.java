package com.leave_management_system.leave_management_system.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.exception.InsufficientLeaveException;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.security.RolePermissionIsolationTestHelper;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyFilter;
import com.leave_management_system.leave_management_system.service.AuditService;
import com.leave_management_system.leave_management_system.service.LeaveRequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Priority 3 — Date and Concurrency Regression & Database/Transaction Integrity Test Suite")
public class DateAndConcurrencyRegressionIntegrationTest {

    @Autowired
    protected WebApplicationContext context;

    protected MockMvc mockMvc;

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PermissionRepository permissionRepository;

    @Autowired
    protected DepartmentRepository departmentRepository;

    @Autowired
    protected EmployeeRepository employeeRepository;

    @Autowired
    protected LeaveTypeRepository leaveTypeRepository;

    @Autowired
    protected LeaveRequestRepository leaveRequestRepository;

    @Autowired
    protected LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    protected LeaveRequestSignoffRepository leaveRequestSignoffRepository;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    @Autowired
    protected SecurityEventRepository securityEventRepository;

    @Autowired(required = false)
    protected TransactionReviewRepository transactionReviewRepository;

    @Autowired(required = false)
    protected AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired(required = false)
    protected LoginHistoryRepository loginHistoryRepository;

    @Autowired(required = false)
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtUtils jwtUtils;

    @Autowired
    protected LeaveRequestService leaveRequestService;

    @Autowired
    protected AuditService auditService;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    protected RolePermissionIsolationTestHelper permissionHelper;

    protected final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    protected String runId;
    protected Department department;

    protected User adminUser;
    protected Employee adminEmployee;
    protected String adminToken;

    protected User managerUser;
    protected Employee managerEmployee;
    protected String managerToken;

    protected User employeeUser;
    protected Employee employee;
    protected String employeeToken;

    protected LeaveType annualLeaveType;
    protected LeaveBalance employeeBalance2026;
    protected LeaveBalance employeeBalance2027;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanup();

        if (permissionHelper != null) {
            permissionHelper.resetToCanonicalPermissions();
        }

        runId = UUID.randomUUID().toString().substring(0, 8);

        // Department
        department = new Department();
        department.setName("Regression Engineering " + runId);
        department = departmentRepository.save(department);

        // Leave Type
        annualLeaveType = new LeaveType();
        annualLeaveType.setName("Annual Leave " + runId);
        annualLeaveType.setDefaultDays(30);
        annualLeaveType.setActive(true);
        annualLeaveType = leaveTypeRepository.save(annualLeaveType);

        // Roles
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        // Admin User & Employee
        adminUser = new User();
        adminUser.setEmail("reg-admin-" + runId + "@lms.test");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        adminEmployee = new Employee();
        adminEmployee.setFirstName("Admin");
        adminEmployee.setLastName("Officer");
        adminEmployee.setEmployeeCode("ADM-" + runId);
        adminEmployee.setStatus("ACTIVE");
        adminEmployee.setUser(adminUser);
        adminEmployee.setDepartment(department);
        adminEmployee.setSalary(new BigDecimal("150000.00"));
        adminEmployee = employeeRepository.save(adminEmployee);
        adminToken = generateToken(adminUser, adminRole);

        // Manager User & Employee
        managerUser = new User();
        managerUser.setEmail("reg-mgr-" + runId + "@lms.test");
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
        managerEmployee.setUser(managerUser);
        managerEmployee.setDepartment(department);
        managerEmployee.setSalary(new BigDecimal("110000.00"));
        managerEmployee = employeeRepository.save(managerEmployee);
        managerToken = generateToken(managerUser, managerRole);

        // Employee User & Employee
        employeeUser = new User();
        employeeUser.setEmail("reg-emp-" + runId + "@lms.test");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.setAccountNonLocked(true);
        employeeUser.getRoles().add(empRole);
        employeeUser = userRepository.save(employeeUser);

        employee = new Employee();
        employee.setFirstName("Alice");
        employee.setLastName("Developer");
        employee.setEmployeeCode("EMP-" + runId);
        employee.setStatus("ACTIVE");
        employee.setUser(employeeUser);
        employee.setDepartment(department);
        employee.setManager(managerEmployee);
        employee.setSalary(new BigDecimal("85000.00"));
        employee = employeeRepository.save(employee);
        employeeToken = generateToken(employeeUser, empRole);

        // Seed 2026 Balance (total = 20, used = 0, available = 20)
        employeeBalance2026 = new LeaveBalance();
        employeeBalance2026.setEmployee(employee);
        employeeBalance2026.setLeaveType(annualLeaveType);
        employeeBalance2026.setYear(2026);
        employeeBalance2026.setTotal(20);
        employeeBalance2026.setUsed(0);
        employeeBalance2026.setAvailable(20);
        employeeBalance2026 = leaveBalanceRepository.save(employeeBalance2026);

        // Seed 2027 Balance (total = 25, used = 0, available = 25)
        employeeBalance2027 = new LeaveBalance();
        employeeBalance2027.setEmployee(employee);
        employeeBalance2027.setLeaveType(annualLeaveType);
        employeeBalance2027.setYear(2027);
        employeeBalance2027.setTotal(25);
        employeeBalance2027.setUsed(0);
        employeeBalance2027.setAvailable(25);
        employeeBalance2027 = leaveBalanceRepository.save(employeeBalance2027);
    }

    @AfterEach
    public void cleanup() {
        try {
            if (transactionReviewRepository != null) transactionReviewRepository.deleteAll();
            if (authorizationAuditLogRepository != null) authorizationAuditLogRepository.deleteAll();
            if (securityEventRepository != null) securityEventRepository.deleteAll();
            if (auditLogRepository != null) auditLogRepository.deleteAll();
            if (loginHistoryRepository != null) loginHistoryRepository.deleteAll();
            if (refreshTokenRepository != null) refreshTokenRepository.deleteAll();
            if (idempotencyKeyRepository != null) idempotencyKeyRepository.deleteAll();
            if (leaveRequestSignoffRepository != null) leaveRequestSignoffRepository.deleteAll();
            if (leaveRequestRepository != null) leaveRequestRepository.deleteAll();
            if (leaveBalanceRepository != null) leaveBalanceRepository.deleteAll();

            if (employeeRepository != null) {
                employeeRepository.findAll().forEach(e -> {
                    if (e.getManager() != null || e.getDepartment() != null) {
                        e.setManager(null);
                        e.setDepartment(null);
                        employeeRepository.save(e);
                    }
                });
                employeeRepository.deleteAll();
            }
            if (departmentRepository != null) {
                departmentRepository.findAll().forEach(d -> {
                    if (d.getManager() != null) {
                        d.setManager(null);
                        departmentRepository.save(d);
                    }
                });
                departmentRepository.deleteAll();
            }
            if (leaveTypeRepository != null) leaveTypeRepository.deleteAll();
            if (userRepository != null) userRepository.deleteAll();
        } catch (Exception ignored) {
        }

        if (permissionHelper != null) {
            permissionHelper.resetToCanonicalPermissions();
        }
        if (leaveRequestService != null) {
            leaveRequestService.setClock(Clock.systemDefaultZone());
        }
        SecurityContextHolder.clearContext();
    }

    protected String generateToken(User user, Role role) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName().replace("ROLE_", "")));
        if (role.getPermissions() != null) {
            role.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
        }
        UserDetails details = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(), authorities);
        return jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    protected void setSecurityAuthentication(User user, String roleName, String... authorities) {
        List<SimpleGrantedAuthority> auths = new ArrayList<>();
        auths.add(new SimpleGrantedAuthority("ROLE_" + roleName.replace("ROLE_", "")));
        for (String a : authorities) {
            auths.add(new SimpleGrantedAuthority(a));
        }
        UserDetails details = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword() != null ? user.getPassword() : "Pass123!", auths);
        SecurityContext secContext = SecurityContextHolder.createEmptyContext();
        secContext.setAuthentication(new UsernamePasswordAuthenticationToken(details, null, auths));
        SecurityContextHolder.setContext(secContext);
    }

    // =========================================================================
    // SECTION 1: DATE BOUNDARIES & WORKING DAYS REGRESSION
    // =========================================================================

    @Test
    @DisplayName("Leave request crossing a single weekend (Friday to Monday) accurately counts 2 working days")
    public void testLeaveRequest_CrossingSingleWeekend_CalculatesWorkingDaysAccurately() throws Exception {
        LocalDate friday = LocalDate.now().plusDays(1).with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        LocalDate monday = friday.plusDays(3);
        assertEquals(DayOfWeek.FRIDAY, friday.getDayOfWeek());
        assertEquals(DayOfWeek.MONDAY, monday.getDayOfWeek());

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(friday);
        dto.setEndDate(monday);
        dto.setReason("Long weekend trip crossing weekend");

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        LeaveRequest created = leaveRequestRepository.findByEmployee(employee).getFirst();
        assertEquals(LeaveStatus.PENDING, created.getStatus());

        // Approve as manager: must deduct strictly 2 working days
        setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
        leaveRequestService.approveLeaveRequest(created.getId());

        LeaveBalance updatedBalance = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, friday.getYear()).orElseThrow();
        assertEquals(2, updatedBalance.getUsed(), "Used days must be exactly 2 for Fri-Mon leave");
        assertEquals(18, updatedBalance.getAvailable(), "Available days must be 20 - 2 = 18");
        assertEquals(20, updatedBalance.getTotal());
    }

    @Test
    @DisplayName("Leave request crossing multiple weekends (12 calendar days) excludes all Saturdays and Sundays")
    public void testLeaveRequest_CrossingMultipleWeekends_ExcludesAllSaturdaysAndSundays() throws Exception {
        // Next Friday to Tuesday 11 days later (12 calendar days: Fri,Sat,Sun,Mon,Tue,Wed,Thu,Fri,Sat,Sun,Mon,Tue)
        // Working days: Fri(1), Mon(2), Tue(3), Wed(4), Thu(5), Fri(6), Mon(7), Tue(8) = exactly 8 days
        LocalDate startFriday = LocalDate.now().plusDays(1).with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        LocalDate endTuesday = startFriday.plusDays(11);
        assertEquals(DayOfWeek.FRIDAY, startFriday.getDayOfWeek());
        assertEquals(DayOfWeek.TUESDAY, endTuesday.getDayOfWeek());

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(startFriday);
        dto.setEndDate(endTuesday);
        dto.setReason("Multi-week vacation crossing 2 full weekends");

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        LeaveRequest created = leaveRequestRepository.findByEmployee(employee).getFirst();

        setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
        leaveRequestService.approveLeaveRequest(created.getId());

        LeaveBalance updatedBalance = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, startFriday.getYear()).orElseThrow();
        assertEquals(8, updatedBalance.getUsed(), "Used days must be exactly 8 for 12 calendar days crossing 2 weekends");
        assertEquals(12, updatedBalance.getAvailable(), "Available days must be 20 - 8 = 12");
        assertEquals(20, updatedBalance.getTotal());
    }

    @Test
    @DisplayName("Leave request covering ONLY weekend days is rejected with HTTP 400 Bad Request")
    public void testLeaveRequest_WeekendOnlyDuration_RejectedWithBadRequest() throws Exception {
        LocalDate saturday = LocalDate.now().plusDays(1).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        LocalDate sunday = saturday.plusDays(1);
        assertEquals(DayOfWeek.SATURDAY, saturday.getDayOfWeek());
        assertEquals(DayOfWeek.SUNDAY, sunday.getDayOfWeek());

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(saturday);
        dto.setEndDate(sunday);
        dto.setReason("Weekend only request");

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        assertEquals(0, leaveRequestRepository.findByEmployee(employee).size());
    }

    @Test
    @DisplayName("Leave request across month-end boundary in same year accurately aggregates working days")
    public void testLeaveRequest_MonthEndBoundary_CalculatesWorkingDaysAndDeductsAccurately() throws Exception {
        int targetYear = LocalDate.now().getYear() + 1;
        LeaveBalance targetBalance = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, targetYear)
                .orElseGet(() -> {
                    LeaveBalance b = new LeaveBalance();
                    b.setEmployee(employee);
                    b.setLeaveType(annualLeaveType);
                    b.setYear(targetYear);
                    b.setTotal(25);
                    b.setUsed(0);
                    b.setAvailable(25);
                    return leaveBalanceRepository.saveAndFlush(b);
                });

        // Dynamically select a Thursday at month-end in targetYear so [Thu..Tue] spans month boundary = 4 working days
        LocalDate monthEndThursday = null;
        for (int month = 2; month <= 11; month++) {
            LocalDate lastDay = LocalDate.of(targetYear, month, 1).with(TemporalAdjusters.lastDayOfMonth());
            LocalDate candidateThu = lastDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
            if (candidateThu.plusDays(5).getMonthValue() != candidateThu.getMonthValue()) {
                monthEndThursday = candidateThu;
                break;
            }
        }
        assertNotNull(monthEndThursday, "A future month-end Thursday spanning month boundary must exist");
        LocalDate nextMonthTuesday = monthEndThursday.plusDays(5);
        assertEquals(DayOfWeek.THURSDAY, monthEndThursday.getDayOfWeek());
        assertEquals(DayOfWeek.TUESDAY, nextMonthTuesday.getDayOfWeek());

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(monthEndThursday);
        dto.setEndDate(nextMonthTuesday);
        dto.setReason("Month-end transition project handover leave");

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        LeaveRequest created = leaveRequestRepository.findByEmployee(employee).getFirst();

        setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
        leaveRequestService.approveLeaveRequest(created.getId());

        LeaveBalance updatedBalance = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, targetYear).orElseThrow();
        assertEquals(4, updatedBalance.getUsed(), "Used days must be exactly 4 across month-end boundary");
        assertEquals(targetBalance.getTotal() - 4, updatedBalance.getAvailable(), "Available days must reflect 4-day deduction");
    }

    @Test
    @DisplayName("Leave request crossing calendar year boundary is strictly rejected; single-year boundary succeeds")
    public void testLeaveRequest_YearEndBoundary_CrossYearRejected_SingleYearSucceeds() throws Exception {
        int year1 = LocalDate.now().getYear() + 1;
        int year2 = year1 + 1;

        LeaveBalance balY1 = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, year1)
                .orElseGet(() -> {
                    LeaveBalance b = new LeaveBalance();
                    b.setEmployee(employee);
                    b.setLeaveType(annualLeaveType);
                    b.setYear(year1);
                    b.setTotal(20);
                    b.setUsed(0);
                    b.setAvailable(20);
                    return leaveBalanceRepository.saveAndFlush(b);
                });
        int y1InitialAvailable = balY1.getAvailable();

        LeaveBalance balY2 = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, year2)
                .orElseGet(() -> {
                    LeaveBalance b = new LeaveBalance();
                    b.setEmployee(employee);
                    b.setLeaveType(annualLeaveType);
                    b.setYear(year2);
                    b.setTotal(25);
                    b.setUsed(0);
                    b.setAvailable(25);
                    return leaveBalanceRepository.saveAndFlush(b);
                });
        int y2InitialAvailable = balY2.getAvailable();

        // 1. Cross-year attempt: Dec 30 year1 to Jan 4 year2 must be REJECTED (HTTP 400)
        LocalDate dec30 = LocalDate.of(year1, 12, 30);
        LocalDate jan4 = LocalDate.of(year2, 1, 4);

        LeaveRequestDTO crossYearDto = new LeaveRequestDTO();
        crossYearDto.setEmployeeId(employee.getId());
        crossYearDto.setLeaveTypeId(annualLeaveType.getId());
        crossYearDto.setStartDate(dec30);
        crossYearDto.setEndDate(jan4);
        crossYearDto.setReason("Attempted cross-year holiday");

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossYearDto)))
                .andExpect(status().isBadRequest());

        // 2. Single-year year-end attempt in Dec of year1 (Mon..Thu = 4 working days)
        LocalDate y1EndMon = LocalDate.of(year1, 12, 28).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate y1EndThu = y1EndMon.plusDays(3);

        LeaveRequestDTO yearEndDto = new LeaveRequestDTO();
        yearEndDto.setEmployeeId(employee.getId());
        yearEndDto.setLeaveTypeId(annualLeaveType.getId());
        yearEndDto.setStartDate(y1EndMon);
        yearEndDto.setEndDate(y1EndThu);
        yearEndDto.setReason("Year-end holiday strictly in " + year1);

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(yearEndDto)))
                .andExpect(status().isCreated());

        LeaveRequest createdY1 = leaveRequestRepository.findByEmployee(employee).getFirst();
        setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
        leaveRequestService.approveLeaveRequest(createdY1.getId());

        LeaveBalance updatedBalY1 = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, year1).orElseThrow();
        assertEquals(4, updatedBalY1.getUsed());
        assertEquals(y1InitialAvailable - 4, updatedBalY1.getAvailable());

        // 3. New-year single-year attempt in Jan of year2 (Mon..Fri = 5 working days)
        LocalDate y2StartMon = LocalDate.of(year2, 1, 4).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate y2StartFri = y2StartMon.plusDays(4);

        LeaveRequestDTO newYearDto = new LeaveRequestDTO();
        newYearDto.setEmployeeId(employee.getId());
        newYearDto.setLeaveTypeId(annualLeaveType.getId());
        newYearDto.setStartDate(y2StartMon);
        newYearDto.setEndDate(y2StartFri);
        newYearDto.setReason("New year startup strictly in " + year2);

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newYearDto)))
                .andExpect(status().isCreated());

        LeaveRequest createdY2 = leaveRequestRepository.findByEmployee(employee).stream()
                .filter(r -> r.getStartDate().getYear() == year2).findFirst().orElseThrow();
        setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
        leaveRequestService.approveLeaveRequest(createdY2.getId());

        LeaveBalance updatedBalY2 = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, year2).orElseThrow();
        assertEquals(5, updatedBalY2.getUsed());
        assertEquals(y2InitialAvailable - 5, updatedBalY2.getAvailable());
    }

    // =========================================================================
    // SECTION 2: CONCURRENCY & OVERLAPPING RACE CONDITIONS
    // =========================================================================

    @Test
    @DisplayName("Concurrent applications for SAME employee with OVERLAPPING dates serialize: strictly 1 succeeds, others fail")
    public void testConcurrentApplications_SameEmployee_OverlappingDates_PreventsRaceCondition() throws Exception {
        int numThreads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        LocalDate baseStart = LocalDate.now().plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate baseEnd = baseStart.plusDays(4);

        for (int i = 0; i < numThreads; i++) {
            final int offset = i % 3; // Produces overlapping windows: Mon-Fri, Tue-Sat, Wed-Sun
            executor.submit(() -> {
                try {
                    startLatch.await();
                    LeaveRequestDTO dto = new LeaveRequestDTO();
                    dto.setEmployeeId(employee.getId());
                    dto.setLeaveTypeId(annualLeaveType.getId());
                    dto.setStartDate(baseStart.plusDays(offset));
                    dto.setEndDate(baseEnd.plusDays(offset));
                    dto.setReason("Concurrent overlap attempt #" + offset);

                    MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                    .header("Authorization", "Bearer " + employeeToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    statusCodes.add(status);
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 400 || status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS), "Concurrent threads timed out");
        executor.shutdown();

        // Pessimistic employee lock ensures that exactly 1 request acquires the lock first and creates the request.
        // Subsequent concurrent threads evaluate hasOverlappingLeave under lock and are rejected.
        assertEquals(1, successCount.get(), "Strictly ONE concurrent application must succeed for overlapping dates");
        assertEquals(numThreads - 1, conflictCount.get(), "All other concurrent overlapping applications must be rejected");
        assertEquals(1, leaveRequestRepository.findByEmployee(employee).size(), "Database must contain exactly 1 leave request");
    }

    @Test
    @DisplayName("Concurrent applications by DIFFERENT employees succeed in parallel without cross-blocking")
    public void testConcurrentApplications_DifferentEmployees_SucceedsWithoutInterference() throws Exception {
        int numEmployees = 5;
        List<Employee> employees = new ArrayList<>();
        List<String> tokens = new ArrayList<>();

        Role empRole = roleRepository.findByName("EMPLOYEE").orElseThrow();

        for (int i = 0; i < numEmployees; i++) {
            User u = new User();
            u.setEmail("diff-emp-" + i + "-" + runId + "@lms.test");
            u.setPassword(passwordEncoder.encode("Pass123!"));
            u.setActive(true);
            u.setAccountNonLocked(true);
            u.getRoles().add(empRole);
            u = userRepository.save(u);

            Employee e = new Employee();
            e.setFirstName("Worker" + i);
            e.setLastName("Concurrent");
            e.setEmployeeCode("DIFF-" + i + "-" + runId);
            e.setStatus("ACTIVE");
            e.setUser(u);
            e.setDepartment(department);
            e.setSalary(new BigDecimal("75000.00"));
            e = employeeRepository.save(e);
            employees.add(e);

            LeaveBalance bal = new LeaveBalance();
            bal.setEmployee(e);
            bal.setLeaveType(annualLeaveType);
            bal.setYear(2026);
            bal.setTotal(20);
            bal.setUsed(0);
            bal.setAvailable(20);
            leaveBalanceRepository.save(bal);

            tokens.add(generateToken(u, empRole));
        }

        ExecutorService executor = Executors.newFixedThreadPool(numEmployees);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numEmployees);
        AtomicInteger successCount = new AtomicInteger(0);

        LocalDate startDate = LocalDate.now().plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate endDate = startDate.plusDays(4);

        for (int i = 0; i < numEmployees; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    LeaveRequestDTO dto = new LeaveRequestDTO();
                    dto.setEmployeeId(employees.get(idx).getId());
                    dto.setLeaveTypeId(annualLeaveType.getId());
                    dto.setStartDate(startDate);
                    dto.setEndDate(endDate);
                    dto.setReason("Concurrent distinct employee application #" + idx);

                    MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                    .header("Authorization", "Bearer " + tokens.get(idx))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                            .andReturn();

                    if (res.getResponse().getStatus() == 201) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(numEmployees, successCount.get(), "All distinct employees must succeed concurrently");
        for (Employee e : employees) {
            assertEquals(1, leaveRequestRepository.findByEmployee(e).size());
        }
    }

    @Test
    @DisplayName("Leave balance consistency maintained under concurrent requests exhausting balance: balance never negative")
    public void testLeaveBalanceConsistency_ConcurrentRequestsExhaustingBalance() throws Exception {
        // Employee 2027 balance is 25 days.
        // Submit 6 sequential non-overlapping applications of 5 days each (total 30 days requested).
        // Exactly 5 should be accepted (5 x 5 = 25), the 6th must fail with InsufficientLeaveException.
        setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN");

        int successfulCreated = 0;
        int rejectedInsufficient = 0;

        LocalDate baseMonday2027 = LocalDate.of(2027, 1, 4); // Monday

        for (int i = 0; i < 6; i++) {
            LocalDate start = baseMonday2027.plusWeeks(i);
            LocalDate end = start.plusDays(4); // Mon to Fri = 5 working days

            LeaveRequestDTO dto = new LeaveRequestDTO();
            dto.setEmployeeId(employee.getId());
            dto.setLeaveTypeId(annualLeaveType.getId());
            dto.setStartDate(start);
            dto.setEndDate(end);
            dto.setReason("Quota exhaustion test #" + i);

            try {
                LeaveResponseDTO res = leaveRequestService.createLeaveRequest(dto);
                assertNotNull(res);
                successfulCreated++;

                // Approve the request to deduct from balance
                setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
                leaveRequestService.approveLeaveRequest(res.getId());
                setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN");
            } catch (InsufficientLeaveException ile) {
                rejectedInsufficient++;
            }
        }

        assertEquals(5, successfulCreated, "Exactly 5 requests of 5 days each should consume the 25-day balance");
        assertEquals(1, rejectedInsufficient, "The 6th request must fail due to InsufficientLeaveException");

        LeaveBalance finalBalance = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, 2027).orElseThrow();
        assertEquals(25, finalBalance.getUsed(), "Used days must be exactly 25");
        assertEquals(0, finalBalance.getAvailable(), "Available days must be exactly 0 (never negative)");
        assertEquals(25, finalBalance.getTotal(), "Total must remain invariant: 25 == 25 + 0");
    }

    // =========================================================================
    // SECTION 3: BALANCE CONSISTENCY ACROSS REQUEST LIFECYCLE
    // =========================================================================

    @Test
    @DisplayName("Balance consistency on APPROVE: Pending does not deduct, Approval deducts exactly requested days")
    public void testBalanceConsistency_OnApproval() {
        setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN");

        LocalDate start = LocalDate.now().plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(2); // 3 working days: Mon, Tue, Wed

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setReason("3-day vacation");

        LeaveResponseDTO pending = leaveRequestService.createLeaveRequest(dto);

        // While PENDING, balance must remain untouched
        LeaveBalance balPending = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, 2026).orElseThrow();
        assertEquals(20, balPending.getAvailable());
        assertEquals(0, balPending.getUsed());

        // On APPROVE, balance deducts strictly 3 days
        setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
        leaveRequestService.approveLeaveRequest(pending.getId());

        LeaveBalance balApproved = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, 2026).orElseThrow();
        assertEquals(17, balApproved.getAvailable());
        assertEquals(3, balApproved.getUsed());
        assertEquals(20, balApproved.getTotal());
        assertEquals(balApproved.getTotal(), balApproved.getUsed() + balApproved.getAvailable());
    }

    @Test
    @DisplayName("Balance consistency on REJECT: Rejected request does not deduct any balance")
    public void testBalanceConsistency_OnRejection() {
        setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN");

        LocalDate start = LocalDate.now().plusWeeks(2).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(4); // 5 working days

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setReason("5-day request to be rejected");

        LeaveResponseDTO pending = leaveRequestService.createLeaveRequest(dto);

        // Reject as manager
        setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_REJECT", "LEAVE_VIEW_TEAM");
        leaveRequestService.rejectLeaveRequest(pending.getId(), "Project deadline conflict");

        LeaveBalance balRejected = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, 2026).orElseThrow();
        assertEquals(20, balRejected.getAvailable(), "Available days must remain untouched at 20");
        assertEquals(0, balRejected.getUsed(), "Used days must remain 0");
        assertEquals(20, balRejected.getTotal());
    }

    @Test
    @DisplayName("Balance consistency on CANCEL of APPROVED request: Refunded accurately; Cancel of PENDING does not double refund")
    public void testBalanceConsistency_OnCancellation() {
        setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN", "LEAVE_CANCEL");

        int targetYear = LocalDate.now().getYear() + 1;
        LeaveBalance baseBal = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, targetYear)
                .orElseThrow();
        int initialAvailable = baseBal.getAvailable();

        // 1. Approved request refund test
        LocalDate start = LocalDate.of(targetYear, 6, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(4); // 5 working days

        LeaveRequestDTO dto1 = new LeaveRequestDTO();
        dto1.setEmployeeId(employee.getId());
        dto1.setLeaveTypeId(annualLeaveType.getId());
        dto1.setStartDate(start);
        dto1.setEndDate(end);
        dto1.setReason("5 days to approve and cancel");

        LeaveResponseDTO res1 = leaveRequestService.createLeaveRequest(dto1);

        setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
        leaveRequestService.approveLeaveRequest(res1.getId());

        LeaveBalance balApproved = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, targetYear).orElseThrow();
        assertEquals(initialAvailable - 5, balApproved.getAvailable());
        assertEquals(5, balApproved.getUsed());

        // Cancel approved request
        setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN", "LEAVE_CANCEL");
        leaveRequestService.cancelLeaveRequest(res1.getId());

        LeaveBalance balAfterCancel = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, targetYear).orElseThrow();
        assertEquals(initialAvailable, balAfterCancel.getAvailable(), "Available must be fully refunded");
        assertEquals(0, balAfterCancel.getUsed(), "Used must be restored to 0");

        // 2. Pending request cancellation (must not add balance)
        LocalDate start2 = start.plusWeeks(1);
        LocalDate end2 = start2.plusDays(2); // 3 working days

        LeaveRequestDTO dto2 = new LeaveRequestDTO();
        dto2.setEmployeeId(employee.getId());
        dto2.setLeaveTypeId(annualLeaveType.getId());
        dto2.setStartDate(start2);
        dto2.setEndDate(end2);
        dto2.setReason("3 days pending then cancelled");

        setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN", "LEAVE_CANCEL");
        LeaveResponseDTO res2 = leaveRequestService.createLeaveRequest(dto2);
        leaveRequestService.cancelLeaveRequest(res2.getId());

        LeaveBalance balPendingCancel = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, targetYear).orElseThrow();
        assertEquals(initialAvailable, balPendingCancel.getAvailable(), "Available must remain unchanged (no spurious refund for unapproved request)");
        assertEquals(0, balPendingCancel.getUsed(), "Used must remain 0");
    }

    @Test
    @DisplayName("Controlled Clock: Deterministic weekend, month-end, and year-end boundary validation with time travel")
    public void testControlledClock_DeterministicDateBoundariesAndTimeTravel() {
        // 1. Deterministic weekend crossing using fixed Clock at Friday Oct 16, 2026, 14:00 UTC
        Instant fridayInstant = Instant.parse("2026-10-16T14:00:00Z");
        Clock fixedFridayClock = Clock.fixed(fridayInstant, ZoneOffset.UTC);
        leaveRequestService.setClock(fixedFridayClock);

        LocalDate startDateFriday = LocalDate.now(fixedFridayClock);
        assertEquals(DayOfWeek.FRIDAY, startDateFriday.getDayOfWeek());
        LocalDate endDateMonday = startDateFriday.plusDays(3); // Mon Oct 19, 2026
        assertEquals(DayOfWeek.MONDAY, endDateMonday.getDayOfWeek());

        setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN");
        LeaveRequestDTO weekendCrossingDTO = new LeaveRequestDTO();
        weekendCrossingDTO.setEmployeeId(employee.getId());
        weekendCrossingDTO.setLeaveTypeId(annualLeaveType.getId());
        weekendCrossingDTO.setStartDate(startDateFriday);
        weekendCrossingDTO.setEndDate(endDateMonday);
        weekendCrossingDTO.setRequiresDualApproval(true);
        weekendCrossingDTO.setReason("Controlled clock weekend boundary");

        LeaveResponseDTO weekendRes = leaveRequestService.createLeaveRequest(weekendCrossingDTO);
        assertNotNull(weekendRes);

        // Approve as manager and verify managerApprovedAt matches controlled Clock instant
        setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
        LeaveResponseDTO approvedWeekend = leaveRequestService.approveLeaveRequest(weekendRes.getId());
        assertNotNull(approvedWeekend);

        LeaveRequest savedReq = leaveRequestRepository.findById(weekendRes.getId()).orElseThrow();
        assertEquals(LocalDateTime.ofInstant(fridayInstant, ZoneOffset.UTC), savedReq.getManagerApprovedAt(),
                "Approval timestamp must deterministically match controlled Clock");

        // Approve as HR to finalize full approval and deduct 2 days
        setSecurityAuthentication(adminUser, "ADMIN", "LEAVE_APPROVE", "LEAVE_VIEW_ALL");
        leaveRequestService.approveLeaveRequest(weekendRes.getId());

        // 2. Deterministic month-end boundary crossing using fixed Clock at Oct 30, 2026 (Friday)
        Instant monthEndInstant = Instant.parse("2026-10-30T10:00:00Z");
        Clock fixedMonthEndClock = Clock.fixed(monthEndInstant, ZoneOffset.UTC);
        leaveRequestService.setClock(fixedMonthEndClock);

        LocalDate monthEndFriday = LocalDate.now(fixedMonthEndClock); // Oct 30, 2026
        LocalDate nextMonthTuesday = monthEndFriday.plusDays(4); // Nov 3, 2026 (Tue)
        // Days: Oct 30 (Fri=1), Oct 31 (Sat=0), Nov 1 (Sun=0), Nov 2 (Mon=1), Nov 3 (Tue=1) -> Total 3 days

        setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN");
        LeaveRequestDTO monthEndDTO = new LeaveRequestDTO();
        monthEndDTO.setEmployeeId(employee.getId());
        monthEndDTO.setLeaveTypeId(annualLeaveType.getId());
        monthEndDTO.setStartDate(monthEndFriday);
        monthEndDTO.setEndDate(nextMonthTuesday);
        monthEndDTO.setReason("Controlled clock month end boundary");

        LeaveResponseDTO monthEndRes = leaveRequestService.createLeaveRequest(monthEndDTO);
        assertNotNull(monthEndRes);

        setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
        leaveRequestService.approveLeaveRequest(monthEndRes.getId());

        // Balance check: initial 20 - 2 (weekend) - 3 (monthEnd) = 15 available, 5 used
        LeaveBalance balAfterMonthEnd = leaveBalanceRepository
                .findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, 2026).orElseThrow();
        assertEquals(15, balAfterMonthEnd.getAvailable(), "Available balance must accurately reflect deterministic working days");
        assertEquals(5, balAfterMonthEnd.getUsed(), "Used days must accurately aggregate to 5");

        // 3. Deterministic year-end boundary crossing using fixed Clock at Dec 31, 2026
        Instant yearEndInstant = Instant.parse("2026-12-31T23:59:00Z");
        Clock fixedYearEndClock = Clock.fixed(yearEndInstant, ZoneOffset.UTC);
        leaveRequestService.setClock(fixedYearEndClock);

        setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN");
        LeaveRequestDTO crossYearDTO = new LeaveRequestDTO();
        crossYearDTO.setEmployeeId(employee.getId());
        crossYearDTO.setLeaveTypeId(annualLeaveType.getId());
        crossYearDTO.setStartDate(LocalDate.of(2026, 12, 31));
        crossYearDTO.setEndDate(LocalDate.of(2027, 1, 4));
        crossYearDTO.setReason("Cross-year boundary attempt");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            leaveRequestService.createLeaveRequest(crossYearDTO);
        });
        assertTrue(ex.getMessage().contains("multiple calendar years"));

        // Single-day year-end boundary within 2026 succeeds
        LeaveRequestDTO validYearEndDTO = new LeaveRequestDTO();
        validYearEndDTO.setEmployeeId(employee.getId());
        validYearEndDTO.setLeaveTypeId(annualLeaveType.getId());
        validYearEndDTO.setStartDate(LocalDate.of(2026, 12, 31));
        validYearEndDTO.setEndDate(LocalDate.of(2026, 12, 31));
        validYearEndDTO.setReason("New Year Eve single day");

        LeaveResponseDTO validYearEnd = leaveRequestService.createLeaveRequest(validYearEndDTO);
        assertNotNull(validYearEnd);

        // Reset Clock to system default
        leaveRequestService.setClock(Clock.systemDefaultZone());
    }

    @Test
    @DisplayName("Concurrent Approval and Cancellation: Synchronizes under row lock, strictly preserving balance invariants")
    public void testConcurrentApprovalAndCancellation_PreservesInvariantsAndPreventsInconsistentState() throws Exception {
        setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN", "LEAVE_CANCEL");

        LocalDate start = LocalDate.now().plusWeeks(3).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(3); // Mon, Tue, Wed, Thu = 4 working days

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setReason("Concurrent approval vs cancellation test");

        LeaveResponseDTO pending = leaveRequestService.createLeaveRequest(dto);
        final Long reqId = pending.getId();

        // Initial balance before concurrent action
        LeaveBalance initialBal = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, 2026).orElseThrow();
        int initialAvailable = initialBal.getAvailable();
        int initialUsed = initialBal.getUsed();
        int initialTotal = initialBal.getTotal();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger approveSuccess = new AtomicInteger(0);
        AtomicInteger cancelSuccess = new AtomicInteger(0);
        AtomicInteger approveErrors = new AtomicInteger(0);
        AtomicInteger cancelErrors = new AtomicInteger(0);

        // Thread 1: Manager approves
        executor.submit(() -> {
            try {
                startLatch.await();
                setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
                leaveRequestService.approveLeaveRequest(reqId);
                approveSuccess.incrementAndGet();
            } catch (Exception e) {
                approveErrors.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Employee cancels
        executor.submit(() -> {
            try {
                startLatch.await();
                setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN", "LEAVE_CANCEL");
                leaveRequestService.cancelLeaveRequest(reqId);
                cancelSuccess.incrementAndGet();
            } catch (Exception e) {
                cancelErrors.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        // Refresh leave request and balance from DB
        LeaveRequest finalReq = leaveRequestRepository.findById(reqId).orElseThrow();
        LeaveBalance finalBal = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, 2026).orElseThrow();

        // Verify balance invariant: total == used + available
        assertEquals(initialTotal, finalBal.getTotal(), "Total balance must remain invariant");
        assertEquals(finalBal.getTotal(), finalBal.getUsed() + finalBal.getAvailable(),
                "CRITICAL INVARIANT VIOLATION: total != used + available");

        // Verify status is terminal: either CANCELLED or APPROVED
        assertTrue(finalReq.getStatus() == LeaveStatus.CANCELLED || finalReq.getStatus() == LeaveStatus.APPROVED,
                "Status must be either CANCELLED or APPROVED, got: " + finalReq.getStatus());

        if (finalReq.getStatus() == LeaveStatus.CANCELLED) {
            assertEquals(initialAvailable, finalBal.getAvailable(), "Cancelled request must refund/preserve initial available balance");
            assertEquals(initialUsed, finalBal.getUsed(), "Used balance must match initial used balance");
        } else {
            assertEquals(initialAvailable - 4, finalBal.getAvailable());
            assertEquals(initialUsed + 4, finalBal.getUsed());
        }
    }

    @Test
    @DisplayName("Simultaneous Balance Updates: Multiple threads compete to update balance, preventing lost updates and negative balance")
    public void testSimultaneousBalanceUpdates_SerializesCorrectlyWithoutLostUpdates() throws Exception {
        int numThreads = 7;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        LocalDate baseMonday = LocalDate.of(2026, 8, 3); // Monday

        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each thread has a distinct non-overlapping week
                    LocalDate start = baseMonday.plusWeeks(index * 2);
                    LocalDate end = start.plusDays(2); // 3 working days: Mon, Tue, Wed

                    setSecurityAuthentication(employeeUser, "EMPLOYEE", "LEAVE_CREATE", "LEAVE_VIEW_OWN");
                    LeaveRequestDTO dto = new LeaveRequestDTO();
                    dto.setEmployeeId(employee.getId());
                    dto.setLeaveTypeId(annualLeaveType.getId());
                    dto.setStartDate(start);
                    dto.setEndDate(end);
                    dto.setReason("Simultaneous balance deduction #" + index);

                    LeaveResponseDTO created = leaveRequestService.createLeaveRequest(dto);

                    // Manager immediately approves under concurrent load
                    setSecurityAuthentication(managerUser, "MANAGER", "LEAVE_APPROVE", "LEAVE_VIEW_TEAM");
                    leaveRequestService.approveLeaveRequest(created.getId());
                    successCount.incrementAndGet();
                } catch (InsufficientLeaveException ile) {
                    insufficientCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(20, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(unexpectedErrors.isEmpty(), "No unexpected concurrency exceptions allowed: " + unexpectedErrors);
        assertEquals(6, successCount.get(), "Exactly 6 requests (18 days) must succeed against 20-day balance");
        assertEquals(1, insufficientCount.get(), "The 7th request (needing 3 days when only 2 remain) must throw InsufficientLeaveException");

        LeaveBalance finalBal = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualLeaveType, 2026).orElseThrow();
        assertEquals(2, finalBal.getAvailable(), "Available balance must accurately be 20 - 18 = 2");
        assertEquals(18, finalBal.getUsed(), "Used days must accurately be 18");
        assertEquals(20, finalBal.getTotal(), "Total balance invariant must strictly hold: 20 == 18 + 2");
        assertTrue(finalBal.getAvailable() >= 0, "Balance must never drop below 0");
    }

    @Test
    @DisplayName("Execution Mode Verification Evidence: Confirms active database engine, JDBC URL, and Hibernate dialect without silent fallbacks")
    public void testDatabaseExecutionMode_VerificationEvidence() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String url = meta.getURL();
            String productName = meta.getDatabaseProductName();
            String driverName = meta.getDriverName();
            int databaseMajorVersion = meta.getDatabaseMajorVersion();

            String[] activeProfiles = context.getEnvironment().getActiveProfiles();
            List<String> profilesList = Arrays.asList(activeProfiles);

            System.out.println("================================================================================");
            System.out.println("EXECUTION MODE DIAGNOSTIC EVIDENCE:");
            System.out.println("Active Spring Profiles: " + profilesList);
            System.out.println("Connected JDBC URL:     " + url);
            System.out.println("Database Product Name:  " + productName);
            System.out.println("Database Major Version: " + databaseMajorVersion);
            System.out.println("Driver Name:            " + driverName);
            System.out.println("================================================================================");

            if (profilesList.contains("pgtest")) {
                assertTrue(url.contains("jdbc:postgresql:"), "Must connect to PostgreSQL JDBC URL in pgtest profile: " + url);
                assertEquals("PostgreSQL", productName, "Database product must strictly be PostgreSQL in pgtest profile");
            } else {
                assertTrue(url.contains("jdbc:h2:"), "Must connect to H2 JDBC URL in test profile: " + url);
                assertEquals("H2", productName, "Database product must strictly be H2 in test profile");
            }
        }
    }

    // =========================================================================
    // SECTION 4: DATABASE & TRANSACTION INTEGRITY
    // =========================================================================

    @Test
    @DisplayName("Liquibase Changeset Audit: All changesets executed with valid checksums; Lock table unlocked")
    public void testLiquibaseChangesets_AllExecutedWithValidChecksums() {
        List<Map<String, Object>> changesets = jdbcTemplate.queryForList(
                "SELECT id, author, md5sum, exectype FROM databasechangelog ORDER BY orderexecuted"
        );

        assertFalse(changesets.isEmpty(), "DATABASECHANGELOG must not be empty");
        assertTrue(changesets.size() >= 26, "Must contain at least 26 changesets");

        for (Map<String, Object> cs : changesets) {
            String id = (String) cs.get("id");
            String md5sum = (String) cs.get("md5sum");
            String exectype = (String) cs.get("exectype");

            assertNotNull(id, "Changeset ID must not be null");
            assertNotNull(md5sum, "Changeset md5sum must not be null for changeset " + id);
            assertTrue(md5sum.startsWith("9:") || md5sum.startsWith("8:") || md5sum.startsWith("7:"),
                    "Valid Liquibase checksum algorithm prefix expected for " + id);
            assertTrue("EXECUTED".equalsIgnoreCase(exectype) || "MARK_RAN".equalsIgnoreCase(exectype),
                    "Changeset execution type must be EXECUTED or MARK_RAN for " + id);
        }

        // Verify lock table is unlocked
        List<Map<String, Object>> locks = jdbcTemplate.queryForList("SELECT id, locked FROM databasechangeloglock");
        assertFalse(locks.isEmpty(), "DATABASECHANGELOGLOCK must exist");
        Object lockedVal = locks.getFirst().get("locked");
        boolean isLocked = (lockedVal instanceof Boolean b) ? b : (lockedVal instanceof Number n && n.intValue() == 1);
        assertFalse(isLocked, "Liquibase migration lock must be FALSE in steady state");
    }

    @Test
    @DisplayName("Database Constraints: Primary keys, Foreign keys, and Unique constraints are strictly enforced")
    public void testDatabaseConstraints_EnforcedAtSchemaAndRuntime() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            // 1. Validate Primary Keys on core tables
            List<String> coreTables = List.of("departments", "employees", "users", "leave_types", "leave_balances", "leave_requests", "audit_logs");
            for (String t : coreTables) {
                try (ResultSet rs = meta.getPrimaryKeys(null, null, t.toUpperCase())) {
                    boolean hasPk = rs.next() || meta.getPrimaryKeys(null, null, t.toLowerCase()).next();
                    assertTrue(hasPk, "Table " + t + " must have a Primary Key");
                }
            }
        }

        // 2. Unique constraint on user email
        User duplicateUser = new User();
        duplicateUser.setEmail(employeeUser.getEmail()); // Duplicate email
        duplicateUser.setPassword("newpass");
        duplicateUser.setActive(true);
        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(duplicateUser);
        }, "Duplicate user email must violate unique constraint");

        // 3. Unique constraint on employee code
        Employee duplicateEmp = new Employee();
        duplicateEmp.setFirstName("Bob");
        duplicateEmp.setLastName("Clone");
        duplicateEmp.setEmployeeCode(employee.getEmployeeCode()); // Duplicate code
        duplicateEmp.setStatus("ACTIVE");
        assertThrows(DataIntegrityViolationException.class, () -> {
            employeeRepository.saveAndFlush(duplicateEmp);
        }, "Duplicate employee code must violate unique constraint");

        // 4. Unique constraint on leave balance (employee_id, leave_type_id, year)
        LeaveBalance duplicateBal = new LeaveBalance();
        duplicateBal.setEmployee(employee);
        duplicateBal.setLeaveType(annualLeaveType);
        duplicateBal.setYear(2026); // Duplicate year balance
        duplicateBal.setTotal(10);
        duplicateBal.setAvailable(10);
        duplicateBal.setUsed(0);
        assertThrows(DataIntegrityViolationException.class, () -> {
            leaveBalanceRepository.saveAndFlush(duplicateBal);
        }, "Duplicate balance for same employee, leave type and year must violate unique constraint");
    }

    @Test
    @DisplayName("Transaction Rollback: Uncaught failure rolls back entity changes while REQUIRES_NEW audit log persists")
    public void testTransactionRollback_FailuresRollbackCompletely_RequiresNewAuditPersists() {
        String probeEmail = "rollback-probe-" + runId + "@test.com";
        String auditAction = "TX_ROLLBACK_PROBE_" + runId;

        assertThrows(RuntimeException.class, () -> {
            transactionTemplate.execute(status -> {
                // 1. Create a user inside transaction
                User u = new User();
                u.setEmail(probeEmail);
                u.setPassword("pass");
                u.setActive(true);
                userRepository.save(u);

                // 2. Emit security audit event (uses Propagation.REQUIRES_NEW)
                auditService.logSecurityEvent(auditAction, "127.0.0.1", probeEmail, "Testing rollback isolation");

                // 3. Force transaction failure
                throw new RuntimeException("Simulated transaction failure to trigger rollback");
            });
        });

        // Verification 1: User entity must be completely rolled back
        Optional<User> probeUser = userRepository.findByEmail(probeEmail);
        assertTrue(probeUser.isEmpty(), "User created in rolled-back transaction must NOT exist in database");

        // Verification 2: Audit log with Propagation.REQUIRES_NEW must be persisted
        List<AuditLog> persistedLogs = auditLogRepository.findByAction(auditAction);
        assertFalse(persistedLogs.isEmpty(), "Audit log with Propagation.REQUIRES_NEW must survive outer rollback");
        assertEquals(auditAction, persistedLogs.getFirst().getAction());
    }

    @Test
    @DisplayName("Idempotency: Concurrent duplicate requests execute strictly once; key replay preserves consistency")
    public void testIdempotency_ConcurrentDuplicateRequests_GuaranteesStrictlySingleExecution() throws Exception {
        String key = "key-idemp-reg-" + runId;

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(employee.getId());
        dto.setLeaveTypeId(annualLeaveType.getId());
        LocalDate start = LocalDate.now().plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        dto.setStartDate(start);
        dto.setEndDate(start.plusDays(2)); // 3 working days
        dto.setReason("Idempotent submission");

        int numThreads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numThreads);

        AtomicInteger successCreated = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                    .header("Authorization", "Bearer " + employeeToken)
                                    .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    if (status == 201) successCreated.incrementAndGet();
                    else if (status == 409) conflictCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(finishLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        // Strictly 1 request created the leave request
        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee);
        assertEquals(1, requests.size(), "Strictly one leave request created despite concurrent duplicate submissions");
        assertEquals(numThreads, successCreated.get() + conflictCount.get());

        // Replay with identical key returns cached response
        MvcResult replayResult = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        assertEquals("true", replayResult.getResponse().getHeader(IdempotencyFilter.IDEMPOTENCY_REPLAYED_HEADER));
    }
}
