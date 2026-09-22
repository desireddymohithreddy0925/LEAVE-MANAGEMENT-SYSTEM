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

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class LeaveRequestServiceConcurrencyIntegrationTest {

    @Autowired
    private LeaveRequestService leaveRequestService;

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
    private RoleRepository roleRepository;

    private Employee testEmployee;
    private LeaveType testLeaveType;
    private LeaveRequest pendingLeaveRequest;

    @BeforeEach
    public void setup() {
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        employeeRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("concurrency@test.com");
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
        balance.setYear(2023);
        balance.setTotal(20);
        balance.setUsed(0);
        balance.setAvailable(20);
        leaveBalanceRepository.save(balance);

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(testEmployee);
        lr.setLeaveType(testLeaveType);
        lr.setStartDate(LocalDate.now().plusDays(1));
        lr.setEndDate(LocalDate.now().plusDays(3)); // 3 working days (if not weekend)
        lr.setStatus(LeaveStatus.PENDING);
        pendingLeaveRequest = leaveRequestRepository.save(lr);
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
                try {
                    latch.await();
                    LeaveRequestDTO dto = new LeaveRequestDTO();
                    dto.setEmployeeId(testEmployee.getId());
                    dto.setLeaveTypeId(testLeaveType.getId());
                    dto.setStartDate(LocalDate.now().plusDays(5));
                    dto.setEndDate(LocalDate.now().plusDays(5));
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
}
