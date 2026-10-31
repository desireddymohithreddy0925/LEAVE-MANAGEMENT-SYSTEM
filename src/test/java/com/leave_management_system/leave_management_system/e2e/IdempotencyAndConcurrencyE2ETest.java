package com.leave_management_system.leave_management_system.e2e;

import com.jayway.jsonpath.JsonPath;
import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class IdempotencyAndConcurrencyE2ETest extends BaseE2ETest {

    private String adminToken;
    private String managerToken;
    private String employeeToken;
    private Long managerEmpId;
    private Long employeeEmpId;
    private Long departmentId;
    private Long leaveTypeId;

    @BeforeEach
    public void setupPermissions() {
        Permission pLeaveCreate = getOrCreatePermission("LEAVE_CREATE", "Create leave request");
        Permission pLeaveApprove = getOrCreatePermission("LEAVE_APPROVE", "Approve leave request");
        Permission pLeaveCancel = getOrCreatePermission("LEAVE_CANCEL", "Cancel leave request");
        Permission pLeaveViewTeam = getOrCreatePermission("LEAVE_VIEW_TEAM", "View team leave requests");
        Permission pLeaveBalManage = getOrCreatePermission("LEAVE_BALANCE_MANAGE", "Manage leave balances");
        Permission pLeaveBalView = getOrCreatePermission("LEAVE_BALANCE_VIEW", "View leave balances");
        Permission pDeptManage = getOrCreatePermission("DEPARTMENT_MANAGE", "Manage departments");
        Permission pDeptView = getOrCreatePermission("DEPARTMENT_VIEW", "View departments");
        Permission pEmpView = getOrCreatePermission("EMPLOYEE_VIEW", "View employee profiles");
        Permission pEmpUpdate = getOrCreatePermission("EMPLOYEE_UPDATE", "Update employee profiles");
        Permission pLeaveTypeManage = getOrCreatePermission("LEAVE_TYPE_MANAGE", "Manage leave types");
        Permission pLeaveTypeView = getOrCreatePermission("LEAVE_TYPE_VIEW", "View leave types");

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        adminRole.getPermissions().addAll(Set.of(
                pLeaveCreate, pLeaveApprove, pLeaveCancel, pLeaveViewTeam,
                pLeaveBalManage, pLeaveBalView, pDeptManage, pDeptView,
                pEmpView, pEmpUpdate, pLeaveTypeManage, pLeaveTypeView
        ));
        roleRepository.save(adminRole);

        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        managerRole.getPermissions().addAll(Set.of(
                pLeaveCreate, pLeaveApprove, pLeaveCancel, pLeaveViewTeam,
                pLeaveBalView, pDeptView, pEmpView
        ));
        roleRepository.save(managerRole);

        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        employeeRole.getPermissions().addAll(Set.of(pLeaveCreate, pLeaveCancel, pLeaveBalView, pDeptView));
        roleRepository.save(employeeRole);
    }

    private void setupBaseData() throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());

        // Admin
        String adminEmail = "admin.idemp." + ts + "@test.com";
        registerUserOverHttp(adminEmail, "AdminPass123!", "Idemp", "Admin");
        elevateUserRole(adminEmail, "ADMIN");
        adminToken = loginUserOverHttp(adminEmail, "AdminPass123!").getAccessToken();

        // Department
        DepartmentRequestDTO deptDto = new DepartmentRequestDTO();
        deptDto.setName("Core Systems " + ts);
        HttpResponse<String> deptResp = post("/api/departments", deptDto, adminToken);
        departmentId = JsonPath.parse(deptResp.body()).read("$.id", Long.class);

        // Leave Type
        LeaveTypeRequestDTO ltDto = new LeaveTypeRequestDTO();
        ltDto.setName("Idemp Vacation " + ts);
        ltDto.setDefaultDays(20);
        HttpResponse<String> ltResp = post("/api/leave-types", ltDto, adminToken);
        leaveTypeId = JsonPath.parse(ltResp.body()).read("$.id", Long.class);

        // Manager
        String mgrEmail = "mgr.idemp." + ts + "@test.com";
        registerUserOverHttp(mgrEmail, "MgrPass123!", "Idemp", "Manager");
        elevateUserRole(mgrEmail, "MANAGER");
        managerToken = loginUserOverHttp(mgrEmail, "MgrPass123!").getAccessToken();
        managerEmpId = employeeRepository.findByUserEmail(mgrEmail).orElseThrow().getId();
        put("/api/employees/" + managerEmpId + "/department?departmentId=" + departmentId, null, adminToken);
        patch("/api/departments/" + departmentId + "/manager?managerId=" + managerEmpId, null, adminToken);

        // Employee
        String empEmail = "emp.idemp." + ts + "@test.com";
        registerUserOverHttp(empEmail, "EmpPass123!", "Idemp", "Staff");
        employeeToken = loginUserOverHttp(empEmail, "EmpPass123!").getAccessToken();
        Employee emp = employeeRepository.findByUserEmail(empEmail).orElseThrow();
        employeeEmpId = emp.getId();

        put("/api/employees/" + employeeEmpId + "/department?departmentId=" + departmentId, null, adminToken);
        patch("/api/employees/" + employeeEmpId + "/manager?managerId=" + managerEmpId, null, adminToken);
    }

    @Test
    void testIdempotencyOverHttp_ReplayedRequestReturnsCachedResponse() throws Exception {
        setupBaseData();

        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(3));
        int leaveYear = monday.getYear();

        // Allocate 10 days balance
        LeaveBalanceRequestDTO balDto = new LeaveBalanceRequestDTO();
        balDto.setEmployeeId(employeeEmpId);
        balDto.setLeaveTypeId(leaveTypeId);
        balDto.setYear(leaveYear);
        balDto.setTotal(10);
        balDto.setAvailable(10);
        post("/api/leave-balances", balDto, adminToken);

        String idempotencyKey = "key-" + UUID.randomUUID();

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveTypeId);
        reqDto.setStartDate(monday);
        reqDto.setEndDate(monday.plusDays(2)); // 3 days
        reqDto.setReason("Idempotent booking");

        // 1. Initial Request with Idempotency-Key
        HttpResponse<String> firstResp = postWithHeaders("/api/leave-requests", reqDto, employeeToken, "Idempotency-Key", idempotencyKey);
        assertEquals(201, firstResp.statusCode());
        Long initialLeaveId = JsonPath.parse(firstResp.body()).read("$.id", Long.class);
        assertNotNull(initialLeaveId);

        // 2. Replay with identical Idempotency-Key and payload -> Returns cached response
        HttpResponse<String> replayedResp = postWithHeaders("/api/leave-requests", reqDto, employeeToken, "Idempotency-Key", idempotencyKey);
        assertEquals(201, replayedResp.statusCode());
        Long replayedLeaveId = JsonPath.parse(replayedResp.body()).read("$.id", Long.class);
        assertEquals(initialLeaveId, replayedLeaveId);

        // 3. Replay with same Idempotency-Key but modified payload -> Returns 409 Conflict
        LeaveRequestDTO modifiedDto = new LeaveRequestDTO();
        modifiedDto.setLeaveTypeId(leaveTypeId);
        modifiedDto.setStartDate(monday);
        modifiedDto.setEndDate(monday.plusDays(4)); // Different end date
        modifiedDto.setReason("Different payload");

        HttpResponse<String> mismatchResp = postWithHeaders("/api/leave-requests", modifiedDto, employeeToken, "Idempotency-Key", idempotencyKey);
        assertEquals(409, mismatchResp.statusCode());
    }

    @Test
    void testConcurrentLeaveApplicationsOverHttp_OverlapProtection() throws Exception {
        setupBaseData();

        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(3));
        int leaveYear = monday.getYear();

        // Allocate 10 days balance
        LeaveBalanceRequestDTO balDto = new LeaveBalanceRequestDTO();
        balDto.setEmployeeId(employeeEmpId);
        balDto.setLeaveTypeId(leaveTypeId);
        balDto.setYear(leaveYear);
        balDto.setTotal(10);
        balDto.setAvailable(10);
        post("/api/leave-balances", balDto, adminToken);

        // Two competing requests for the EXACT same date range (Mon to Wed)
        LeaveRequestDTO req1 = new LeaveRequestDTO();
        req1.setLeaveTypeId(leaveTypeId);
        req1.setStartDate(monday);
        req1.setEndDate(monday.plusDays(2));
        req1.setReason("Concurrent overlap request 1");

        LeaveRequestDTO req2 = new LeaveRequestDTO();
        req2.setLeaveTypeId(leaveTypeId);
        req2.setStartDate(monday);
        req2.setEndDate(monday.plusDays(2));
        req2.setReason("Concurrent overlap request 2");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        Callable<HttpResponse<String>> task1 = () -> post("/api/leave-requests", req1, employeeToken);
        Callable<HttpResponse<String>> task2 = () -> post("/api/leave-requests", req2, employeeToken);

        Future<HttpResponse<String>> f1 = executor.submit(task1);
        Future<HttpResponse<String>> f2 = executor.submit(task2);

        HttpResponse<String> r1 = f1.get();
        HttpResponse<String> r2 = f2.get();
        executor.shutdown();

        if (r1.statusCode() == 201) successCount.incrementAndGet();
        else if (r1.statusCode() == 400 || r1.statusCode() == 409) rejectedCount.incrementAndGet();

        if (r2.statusCode() == 201) successCount.incrementAndGet();
        else if (r2.statusCode() == 400 || r2.statusCode() == 409) rejectedCount.incrementAndGet();

        // Exactly 1 must succeed and 1 must be rejected due to overlap prevention
        assertEquals(1, successCount.get(), "Expected exactly 1 request to succeed");
        assertEquals(1, rejectedCount.get(), "Expected exactly 1 request to be rejected");
    }

    @Test
    void testConcurrentLeaveApprovalsOverHttp_BalanceProtection() throws Exception {
        setupBaseData();

        LocalDate monday1 = getNextMonday(LocalDate.now().plusWeeks(3));
        LocalDate monday2 = getNextMonday(LocalDate.now().plusWeeks(5));
        int leaveYear = monday1.getYear();

        // Allocate ONLY 5 days balance
        LeaveBalanceRequestDTO balDto = new LeaveBalanceRequestDTO();
        balDto.setEmployeeId(employeeEmpId);
        balDto.setLeaveTypeId(leaveTypeId);
        balDto.setYear(leaveYear);
        balDto.setTotal(5);
        balDto.setAvailable(5);
        post("/api/leave-balances", balDto, adminToken);

        // Submit request 1 for 4 days
        LeaveRequestDTO req1 = new LeaveRequestDTO();
        req1.setLeaveTypeId(leaveTypeId);
        req1.setStartDate(monday1);
        req1.setEndDate(monday1.plusDays(3)); // 4 working days
        req1.setReason("Booking week 3");
        HttpResponse<String> resp1 = post("/api/leave-requests", req1, employeeToken);
        assertEquals(201, resp1.statusCode());
        Long leaveId1 = JsonPath.parse(resp1.body()).read("$.id", Long.class);

        // Submit request 2 for 4 days (different week)
        LeaveRequestDTO req2 = new LeaveRequestDTO();
        req2.setLeaveTypeId(leaveTypeId);
        req2.setStartDate(monday2);
        req2.setEndDate(monday2.plusDays(3)); // 4 working days
        req2.setReason("Booking week 5");
        HttpResponse<String> resp2 = post("/api/leave-requests", req2, employeeToken);
        assertEquals(201, resp2.statusCode());
        Long leaveId2 = JsonPath.parse(resp2.body()).read("$.id", Long.class);

        // Two concurrent approvals competing for the 5-day balance (4 + 4 = 8 > 5)
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger approvedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        Callable<HttpResponse<String>> approveTask1 = () -> patch("/api/leave-requests/" + leaveId1 + "/approve", null, managerToken);
        Callable<HttpResponse<String>> approveTask2 = () -> patch("/api/leave-requests/" + leaveId2 + "/approve", null, managerToken);

        Future<HttpResponse<String>> f1 = executor.submit(approveTask1);
        Future<HttpResponse<String>> f2 = executor.submit(approveTask2);

        HttpResponse<String> r1 = f1.get();
        HttpResponse<String> r2 = f2.get();
        executor.shutdown();

        if (r1.statusCode() == 200) approvedCount.incrementAndGet();
        else if (r1.statusCode() == 400 || r1.statusCode() == 409) rejectedCount.incrementAndGet();

        if (r2.statusCode() == 200) approvedCount.incrementAndGet();
        else if (r2.statusCode() == 400 || r2.statusCode() == 409) rejectedCount.incrementAndGet();

        // Exactly 1 approval must succeed and 1 must fail due to insufficient balance
        assertEquals(1, approvedCount.get(), "Expected exactly 1 approval to succeed");
        assertEquals(1, rejectedCount.get(), "Expected exactly 1 approval to fail due to balance depletion");

        // Verify balance in database is 1 (5 - 4 = 1), never negative
        HttpResponse<String> balResp = get("/api/leave-balances/me", employeeToken);
        assertEquals(200, balResp.statusCode());
        int remainingAvailable = JsonPath.parse(balResp.body()).read("$[0].available", Integer.class);
        assertEquals(1, remainingAvailable, "Available balance should be 5 - 4 = 1");
    }
}
