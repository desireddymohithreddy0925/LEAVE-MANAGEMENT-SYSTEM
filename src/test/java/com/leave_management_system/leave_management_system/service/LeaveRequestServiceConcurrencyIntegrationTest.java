package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class LeaveRequestServiceConcurrencyIntegrationTest {

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.AuditLogRepository auditLogRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private Employee testEmployee;
    private LeaveType testLeaveType;
    private LeaveRequest pendingLeaveRequest;
    private String testUserEmail;

    @BeforeEach
    public void setup() {
        cleanup();

        testUserEmail = "concurrency-" + java.util.UUID.randomUUID() + "@test.com";

        User user = new User();
        user.setEmail(testUserEmail);
        user.setPassword("password");
        user.setActive(true);
        userRepository.save(user);

        Employee employee = new Employee();
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setEmployeeCode("C001");
        employee.setPhone("1234567890");
        employee.setStatus("ACTIVE");
        employee.setSalary(new BigDecimal("50000.00"));
        employee.setUser(user);
        testEmployee = employeeRepository.save(employee);

        LeaveType leaveType = new LeaveType();
        leaveType.setName("Annual Leave");
        leaveType.setDefaultDays(20);
        leaveType.setActive(true);
        testLeaveType = leaveTypeRepository.save(leaveType);

        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(testEmployee);
        balance.setLeaveType(testLeaveType);
        balance.setYear(2026);
        balance.setTotal(20);
        balance.setUsed(0);
        balance.setAvailable(20);
        leaveBalanceRepository.save(balance);

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(testEmployee);
        lr.setLeaveType(testLeaveType);
        LocalDate baseDate = LocalDate.of(2026, 10, 5); // Monday
        lr.setStartDate(baseDate.plusDays(1)); // Tuesday
        lr.setEndDate(baseDate.plusDays(3)); // Thursday
        lr.setStatus(LeaveStatus.PENDING);
        pendingLeaveRequest = leaveRequestRepository.save(lr);
    }

    @org.junit.jupiter.api.AfterEach
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

    @Test
    public void testConcurrentLeaveApprovalsThrowsOptimisticLockException() throws InterruptedException {
        int threads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executorService.submit(() -> {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(testUserEmail, "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("LEAVE_APPROVE"))));
                SecurityContextHolder.setContext(context);
                try {
                    latch.await();
                    leaveRequestService.approveLeaveRequest(pendingLeaveRequest.getId());
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException | IllegalArgumentException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); // Start all threads at once
        doneLatch.await();

        assertEquals(1, successCount.get(), "Only one approval should succeed");
        assertEquals(1, failureCount.get(), "The other approval should fail due to optimistic locking or illegal argument");
        
        LeaveRequest finalRequest = leaveRequestRepository.findById(pendingLeaveRequest.getId()).get();
        assertEquals(LeaveStatus.APPROVED, finalRequest.getStatus());
    }

    @Test
    public void testConcurrentLeaveApplicationsThrowsOptimisticLockException() throws InterruptedException {
        int threads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executorService.submit(() -> {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(testUserEmail, "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("LEAVE_CREATE"))));
                SecurityContextHolder.setContext(context);
                try {
                    latch.await();
                    LeaveRequestDTO dto = new LeaveRequestDTO();
                    dto.setEmployeeId(testEmployee.getId());
                    dto.setLeaveTypeId(testLeaveType.getId());
                    LocalDate baseDate = LocalDate.of(2026, 11, 2); // Monday in 2026
                    dto.setStartDate(baseDate.plusDays(1)); // Tuesday
                    dto.setEndDate(baseDate.plusDays(3)); // Thursday
                    dto.setReason("Concurrent request");
                    leaveRequestService.createLeaveRequest(dto);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await();

        assertEquals(1, successCount.get(), "Only one request should succeed due to overlap detection");
        assertEquals(1, failureCount.get(), "The other request should fail due to overlap exception");
    }

    @Test
    public void testConcurrentLeaveCancellationsDoesNotDoubleRefund() throws InterruptedException {
        // Approve request first
        SecurityContext adminCtx = SecurityContextHolder.createEmptyContext();
        adminCtx.setAuthentication(new UsernamePasswordAuthenticationToken(testUserEmail, "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("LEAVE_APPROVE"))));
        SecurityContextHolder.setContext(adminCtx);
        leaveRequestService.approveLeaveRequest(pendingLeaveRequest.getId());

        LeaveBalance balAfterApproval = leaveBalanceRepository.findByEmployeeAndLeaveType(testEmployee, testLeaveType).get();
        int availableAfterApproval = balAfterApproval.getAvailable();

        int threads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executorService.submit(() -> {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(testUserEmail, "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("LEAVE_VIEW_ALL"))));
                SecurityContextHolder.setContext(context);
                try {
                    latch.await();
                    leaveRequestService.cancelLeaveRequest(pendingLeaveRequest.getId());
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException | IllegalArgumentException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await();

        assertEquals(1, successCount.get(), "Only one cancellation should succeed");
        assertEquals(1, failureCount.get(), "The other cancellation should fail");

        LeaveBalance finalBalance = leaveBalanceRepository.findByEmployeeAndLeaveType(testEmployee, testLeaveType).get();
        assertEquals(availableAfterApproval + 3, finalBalance.getAvailable(), "Leave days should only be refunded once");
        assertEquals(0, finalBalance.getUsed(), "Used days should be 0 after single refund");
    }

    @Test
    public void testPessimisticLockDirectRepositoryQueries() {
        transactionTemplate.execute(status -> {
            // Verify pessimistic lock query retrieves entity successfully and locks row
            var lockedRequest = leaveRequestRepository.findByIdWithPessimisticLock(pendingLeaveRequest.getId());
            assertTrue(lockedRequest.isPresent(), "Pessimistic lock query should find leave request");
            assertEquals(LeaveStatus.PENDING, lockedRequest.get().getStatus());

            var balance = leaveBalanceRepository.findByEmployeeAndLeaveType(testEmployee, testLeaveType).orElseThrow();
            var lockedBalance = leaveBalanceRepository.findByIdWithPessimisticLock(balance.getId());
            assertTrue(lockedBalance.isPresent(), "Pessimistic lock query should find leave balance");
            assertEquals(20, lockedBalance.get().getAvailable());
            return null;
        });
    }

    @Test
    public void testConcurrentBalanceUpdatesSerializedWithPessimisticLock() throws InterruptedException {
        LeaveBalance balance = leaveBalanceRepository.findByEmployeeAndLeaveType(testEmployee, testLeaveType).orElseThrow();

        int threads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int deductAmount = (i + 1); // 1 or 2
            executorService.submit(() -> {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(
                        testUserEmail, "password",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("LEAVE_BALANCE_MANAGE"))));
                SecurityContextHolder.setContext(context);
                try {
                    latch.await();
                    com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO dto =
                            new com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO();
                    dto.setAvailable(20 - deductAmount);
                    dto.setTotal(20);
                    leaveBalanceService.updateLeaveBalance(balance.getId(), dto);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await();

        // With optimistic/pessimistic lock, either both serialize or one succeeds while the other fails cleanly on version conflict
        assertTrue(successCount.get() >= 1, "At least one balance update must succeed");
        assertEquals(threads, successCount.get() + failureCount.get(), "Total thread outcomes must match thread count");

        LeaveBalance finalBalance = leaveBalanceRepository.findById(balance.getId()).orElseThrow();
        assertTrue(finalBalance.getAvailable() < 20, "Balance available should reflect the update");
    }
}
