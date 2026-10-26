package com.leave_management_system.leave_management_system.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentLeaveApplicationIntegrationTest {

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

    private Employee employee1;
    private String employee1Token;

    private Employee employee2;
    private String employee2Token;

    private LeaveType annualLeaveType;
    private LeaveType sickLeaveType;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        cleanup();

        // 1. Setup Permissions
        List<String> perms = List.of("LEAVE_CREATE", "LEAVE_CANCEL", "LEAVE_VIEW_ALL");
        Set<Permission> permissions = new HashSet<>();
        for (String permName : perms) {
            Permission p = permissionRepository.findByName(permName)
                    .orElseGet(() -> permissionRepository.save(new Permission(permName, permName + " permission")));
            permissions.add(p);
        }

        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        empRole.getPermissions().addAll(permissions);
        roleRepository.save(empRole);

        // 2. Setup Leave Types
        LeaveType lt1 = new LeaveType();
        lt1.setName("Annual Leave " + UUID.randomUUID().toString().substring(0, 5));
        lt1.setDefaultDays(20);
        lt1.setActive(true);
        annualLeaveType = leaveTypeRepository.save(lt1);

        LeaveType lt2 = new LeaveType();
        lt2.setName("Sick Leave " + UUID.randomUUID().toString().substring(0, 5));
        lt2.setDefaultDays(10);
        lt2.setActive(true);
        sickLeaveType = leaveTypeRepository.save(lt2);

        // 3. Setup Employee 1
        User user1 = new User();
        user1.setEmail("emp1-" + UUID.randomUUID() + "@test.com");
        user1.setPassword(passwordEncoder.encode("Pass123!"));
        user1.setActive(true);
        user1.setAccountNonLocked(true);
        user1.setRoles(Set.of(empRole));
        user1 = userRepository.save(user1);
        employee1Token = jwtUtils.generateTokenFromUsername(user1.getEmail());

        Employee emp1 = new Employee();
        emp1.setFirstName("Alice");
        emp1.setLastName("Concur");
        emp1.setEmployeeCode("APPL_1_" + System.currentTimeMillis() % 10000);
        emp1.setPhone("9876543201");
        emp1.setStatus("ACTIVE");
        emp1.setSalary(new BigDecimal("70000.00"));
        emp1.setUser(user1);
        employee1 = employeeRepository.save(emp1);

        LeaveBalance bal1Annual = new LeaveBalance();
        bal1Annual.setEmployee(employee1);
        bal1Annual.setLeaveType(annualLeaveType);
        bal1Annual.setYear(2026);
        bal1Annual.setTotal(20);
        bal1Annual.setUsed(0);
        bal1Annual.setAvailable(20);
        leaveBalanceRepository.save(bal1Annual);

        LeaveBalance bal1Sick = new LeaveBalance();
        bal1Sick.setEmployee(employee1);
        bal1Sick.setLeaveType(sickLeaveType);
        bal1Sick.setYear(2026);
        bal1Sick.setTotal(10);
        bal1Sick.setUsed(0);
        bal1Sick.setAvailable(10);
        leaveBalanceRepository.save(bal1Sick);

        // 4. Setup Employee 2
        User user2 = new User();
        user2.setEmail("emp2-" + UUID.randomUUID() + "@test.com");
        user2.setPassword(passwordEncoder.encode("Pass123!"));
        user2.setActive(true);
        user2.setAccountNonLocked(true);
        user2.setRoles(Set.of(empRole));
        user2 = userRepository.save(user2);
        employee2Token = jwtUtils.generateTokenFromUsername(user2.getEmail());

        Employee emp2 = new Employee();
        emp2.setFirstName("Bob");
        emp2.setLastName("Concur");
        emp2.setEmployeeCode("APPL_2_" + System.currentTimeMillis() % 10000);
        emp2.setPhone("9876543202");
        emp2.setStatus("ACTIVE");
        emp2.setSalary(new BigDecimal("75000.00"));
        emp2.setUser(user2);
        employee2 = employeeRepository.save(emp2);

        LeaveBalance bal2Annual = new LeaveBalance();
        bal2Annual.setEmployee(employee2);
        bal2Annual.setLeaveType(annualLeaveType);
        bal2Annual.setYear(2026);
        bal2Annual.setTotal(20);
        bal2Annual.setUsed(0);
        bal2Annual.setAvailable(20);
        leaveBalanceRepository.save(bal2Annual);
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

    @Test
    public void concurrentSimultaneousApplications_sameEmployee_identicalDates_exactlyOneSucceeds_otherFailsWithOverlap() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 3); // Tuesday
        LocalDate end = LocalDate.of(2026, 11, 5);   // Thursday (3 days)

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
                    LeaveRequestDTO dto = new LeaveRequestDTO();
                    dto.setEmployeeId(employee1.getId());
                    dto.setLeaveTypeId(annualLeaveType.getId());
                    dto.setStartDate(start);
                    dto.setEndDate(end);
                    dto.setReason("Simultaneous duplicate application test");

                    MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                    .header("Authorization", "Bearer " + employee1Token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    statusCodes.add(status);
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

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one application must succeed. Status codes: " + statusCodes);
        assertEquals(1, failureCount.get(), "The competing identical application must fail due to overlap. Status codes: " + statusCodes);

        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(1, requests.size(), "Only one leave request must be stored in database for employee1");
        assertEquals(LeaveStatus.PENDING, requests.get(0).getStatus());
    }

    @Test
    public void concurrentSimultaneousApplications_sameEmployee_differentLeaveTypes_overlappingDates_exactlyOneSucceeds() throws Exception {
        // Request 1: Annual Leave (Nov 10 - Nov 12)
        // Request 2: Sick Leave (Nov 11 - Nov 13)
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: Annual Leave
        executor.submit(() -> {
            try {
                startLatch.await();
                LeaveRequestDTO dto = new LeaveRequestDTO();
                dto.setEmployeeId(employee1.getId());
                dto.setLeaveTypeId(annualLeaveType.getId());
                dto.setStartDate(LocalDate.of(2026, 11, 10));
                dto.setEndDate(LocalDate.of(2026, 11, 12));
                dto.setReason("Annual leave concurrent test");

                MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                .header("Authorization", "Bearer " + employee1Token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)))
                        .andReturn();

                int status = res.getResponse().getStatus();
                statusCodes.add(status);
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

        // Thread 2: Sick Leave (Overlapping dates)
        executor.submit(() -> {
            try {
                startLatch.await();
                LeaveRequestDTO dto = new LeaveRequestDTO();
                dto.setEmployeeId(employee1.getId());
                dto.setLeaveTypeId(sickLeaveType.getId());
                dto.setStartDate(LocalDate.of(2026, 11, 11));
                dto.setEndDate(LocalDate.of(2026, 11, 13));
                dto.setReason("Sick leave concurrent overlapping test");

                MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                .header("Authorization", "Bearer " + employee1Token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)))
                        .andReturn();

                int status = res.getResponse().getStatus();
                statusCodes.add(status);
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

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one overlapping request must succeed. Codes: " + statusCodes);
        assertEquals(1, failureCount.get(), "The competing overlapping request must be rejected. Codes: " + statusCodes);

        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(1, requests.size(), "Only one leave request can exist for overlapping intervals");
    }

    @Test
    public void concurrentSimultaneousApplications_sameEmployee_nonOverlappingDates_bothSucceed() throws Exception {
        // Request 1: Nov 3 - Nov 5 (Tuesday - Thursday)
        // Request 2: Nov 17 - Nov 19 (Tuesday - Thursday)
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                startLatch.await();
                LeaveRequestDTO dto = new LeaveRequestDTO();
                dto.setEmployeeId(employee1.getId());
                dto.setLeaveTypeId(annualLeaveType.getId());
                dto.setStartDate(LocalDate.of(2026, 11, 3));
                dto.setEndDate(LocalDate.of(2026, 11, 5));
                dto.setReason("Non-overlapping period 1");

                MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                .header("Authorization", "Bearer " + employee1Token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)))
                        .andReturn();

                statusCodes.add(res.getResponse().getStatus());
                if (res.getResponse().getStatus() == 200 || res.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                // handle
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                LeaveRequestDTO dto = new LeaveRequestDTO();
                dto.setEmployeeId(employee1.getId());
                dto.setLeaveTypeId(annualLeaveType.getId());
                dto.setStartDate(LocalDate.of(2026, 11, 17));
                dto.setEndDate(LocalDate.of(2026, 11, 19));
                dto.setReason("Non-overlapping period 2");

                MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                .header("Authorization", "Bearer " + employee1Token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)))
                        .andReturn();

                statusCodes.add(res.getResponse().getStatus());
                if (res.getResponse().getStatus() == 200 || res.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                // handle
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        assertEquals(2, successCount.get(), "Both non-overlapping applications must succeed with HTTP 200/201. Codes: " + statusCodes);

        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(2, requests.size(), "Both non-overlapping leave requests must be saved");
    }

    @Test
    public void concurrentSimultaneousApplications_differentEmployees_sameDates_bothSucceedInParallel() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 24);
        LocalDate end = LocalDate.of(2026, 11, 26);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        // Employee 1
        executor.submit(() -> {
            try {
                startLatch.await();
                LeaveRequestDTO dto = new LeaveRequestDTO();
                dto.setEmployeeId(employee1.getId());
                dto.setLeaveTypeId(annualLeaveType.getId());
                dto.setStartDate(start);
                dto.setEndDate(end);
                dto.setReason("Employee 1 vacation");

                MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                .header("Authorization", "Bearer " + employee1Token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)))
                        .andReturn();

                statusCodes.add(res.getResponse().getStatus());
                if (res.getResponse().getStatus() == 200 || res.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                // handle
            } finally {
                doneLatch.countDown();
            }
        });

        // Employee 2
        executor.submit(() -> {
            try {
                startLatch.await();
                LeaveRequestDTO dto = new LeaveRequestDTO();
                dto.setEmployeeId(employee2.getId());
                dto.setLeaveTypeId(annualLeaveType.getId());
                dto.setStartDate(start);
                dto.setEndDate(end);
                dto.setReason("Employee 2 vacation");

                MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                .header("Authorization", "Bearer " + employee2Token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)))
                        .andReturn();

                statusCodes.add(res.getResponse().getStatus());
                if (res.getResponse().getStatus() == 200 || res.getResponse().getStatus() == 201) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                // handle
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        assertEquals(2, successCount.get(), "Both distinct employees must succeed in parallel. Codes: " + statusCodes);
        assertEquals(1, leaveRequestRepository.findByEmployee(employee1).size());
        assertEquals(1, leaveRequestRepository.findByEmployee(employee2).size());
    }

    @Test
    public void concurrentSimultaneousApplications_fiveThreads_sameDates_exactlyOneSucceeds() throws Exception {
        LocalDate start = LocalDate.of(2026, 12, 1);
        LocalDate end = LocalDate.of(2026, 12, 3);

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
                    LeaveRequestDTO dto = new LeaveRequestDTO();
                    dto.setEmployeeId(employee1.getId());
                    dto.setLeaveTypeId(annualLeaveType.getId());
                    dto.setStartDate(start);
                    dto.setEndDate(end);
                    dto.setReason("Five thread race test");

                    MvcResult res = mockMvc.perform(post("/api/leave-requests")
                                    .header("Authorization", "Bearer " + employee1Token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(dto)))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    statusCodes.add(status);
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

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent execution timed out");
        executor.shutdown();

        assertEquals(1, successCount.get(), "Only 1 application must succeed out of 5 simultaneous requests. Codes: " + statusCodes);
        assertEquals(4, failureCount.get(), "Remaining 4 applications must be rejected due to overlap. Codes: " + statusCodes);

        List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(employee1);
        assertEquals(1, requests.size(), "Database must store strictly 1 leave request for employee1");
    }
}
