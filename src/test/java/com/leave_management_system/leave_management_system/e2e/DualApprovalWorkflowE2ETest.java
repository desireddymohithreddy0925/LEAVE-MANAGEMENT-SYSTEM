package com.leave_management_system.leave_management_system.e2e;

import com.jayway.jsonpath.JsonPath;
import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DualApprovalWorkflowE2ETest extends BaseE2ETest {

    private String adminToken;
    private String managerToken;
    private String hrToken;
    private String employeeToken;

    private Long managerEmpId;
    private Long hrEmpId;
    private Long employeeEmpId;
    private Long departmentId;
    private Long leaveTypeId;

    @BeforeEach
    public void setupRolesAndPermissions() {
        Permission pLeaveCreate = getOrCreatePermission("LEAVE_CREATE", "Create leave request");
        Permission pLeaveApprove = getOrCreatePermission("LEAVE_APPROVE", "Approve leave request");
        Permission pLeaveReject = getOrCreatePermission("LEAVE_REJECT", "Reject leave request");
        Permission pLeaveCancel = getOrCreatePermission("LEAVE_CANCEL", "Cancel leave request");
        Permission pLeaveViewTeam = getOrCreatePermission("LEAVE_VIEW_TEAM", "View team leave requests");
        Permission pLeaveViewAll = getOrCreatePermission("LEAVE_VIEW_ALL", "View all leave requests");
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
                pLeaveCreate, pLeaveApprove, pLeaveReject, pLeaveCancel,
                pLeaveViewTeam, pLeaveViewAll, pLeaveBalManage, pLeaveBalView,
                pDeptManage, pDeptView, pEmpView, pEmpUpdate,
                pLeaveTypeManage, pLeaveTypeView
        ));
        roleRepository.save(adminRole);

        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        managerRole.getPermissions().addAll(Set.of(
                pLeaveCreate, pLeaveApprove, pLeaveReject, pLeaveCancel,
                pLeaveViewTeam, pLeaveBalView, pDeptView, pEmpView
        ));
        roleRepository.save(managerRole);

        Role hrRole = roleRepository.findByName("HR").orElseGet(() -> roleRepository.save(new Role("HR")));
        hrRole.getPermissions().addAll(Set.of(
                pLeaveCreate, pLeaveApprove, pLeaveReject, pLeaveCancel,
                pLeaveViewAll, pLeaveBalManage, pLeaveBalView, pDeptView, pEmpView
        ));
        roleRepository.save(hrRole);

        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        employeeRole.getPermissions().addAll(Set.of(
                pLeaveCreate, pLeaveCancel, pLeaveBalView, pDeptView
        ));
        roleRepository.save(employeeRole);
    }

    private void setupDepartmentAndStaff() throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());

        // 1. Admin
        String adminEmail = "admin.dual." + ts + "@test.com";
        registerUserOverHttp(adminEmail, "AdminPass123!", "Admin", "User");
        elevateUserRole(adminEmail, "ADMIN");
        adminToken = loginUserOverHttp(adminEmail, "AdminPass123!").getAccessToken();

        // 2. Department
        DepartmentRequestDTO deptDto = new DepartmentRequestDTO();
        deptDto.setName("Operations " + ts);
        HttpResponse<String> deptResp = post("/api/departments", deptDto, adminToken);
        departmentId = JsonPath.parse(deptResp.body()).read("$.id", Long.class);

        // 3. Leave Type
        LeaveTypeRequestDTO ltDto = new LeaveTypeRequestDTO();
        ltDto.setName("Dual Vacation " + ts);
        ltDto.setDefaultDays(25);
        HttpResponse<String> ltResp = post("/api/leave-types", ltDto, adminToken);
        leaveTypeId = JsonPath.parse(ltResp.body()).read("$.id", Long.class);

        // 4. Manager
        String mgrEmail = "mgr.dual." + ts + "@test.com";
        registerUserOverHttp(mgrEmail, "ManagerPass123!", "Dual", "Manager");
        elevateUserRole(mgrEmail, "MANAGER");
        managerToken = loginUserOverHttp(mgrEmail, "ManagerPass123!").getAccessToken();
        managerEmpId = employeeRepository.findByUserEmail(mgrEmail).orElseThrow().getId();

        put("/api/employees/" + managerEmpId + "/department?departmentId=" + departmentId, null, adminToken);
        patch("/api/departments/" + departmentId + "/manager?managerId=" + managerEmpId, null, adminToken);

        // 5. HR
        String hrEmail = "hr.dual." + ts + "@test.com";
        registerUserOverHttp(hrEmail, "HrPass123!", "Dual", "HR");
        elevateUserRole(hrEmail, "HR");
        hrToken = loginUserOverHttp(hrEmail, "HrPass123!").getAccessToken();
        hrEmpId = employeeRepository.findByUserEmail(hrEmail).orElseThrow().getId();
        put("/api/employees/" + hrEmpId + "/department?departmentId=" + departmentId, null, adminToken);

        // 6. Employee
        String empEmail = "emp.dual." + ts + "@test.com";
        registerUserOverHttp(empEmail, "EmpPass123!", "Dual", "Staff");
        employeeToken = loginUserOverHttp(empEmail, "EmpPass123!").getAccessToken();
        employeeEmpId = employeeRepository.findByUserEmail(empEmail).orElseThrow().getId();

        put("/api/employees/" + employeeEmpId + "/department?departmentId=" + departmentId, null, adminToken);
        patch("/api/employees/" + employeeEmpId + "/manager?managerId=" + managerEmpId, null, adminToken);
    }

    @Test
    void testDualApprovalEndToEndWorkflowOverHttp() throws Exception {
        setupDepartmentAndStaff();

        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(3));
        int leaveYear = monday.getYear();

        // Allocate 15 days Leave Balance
        LeaveBalanceRequestDTO balDto = new LeaveBalanceRequestDTO();
        balDto.setEmployeeId(employeeEmpId);
        balDto.setLeaveTypeId(leaveTypeId);
        balDto.setYear(leaveYear);
        balDto.setTotal(15);
        balDto.setAvailable(15);
        HttpResponse<String> balResp = post("/api/leave-balances", balDto, adminToken);
        assertEquals(201, balResp.statusCode());

        // Employee applies with requiresDualApproval = true (Monday to Wednesday, 3 days)
        LocalDate wednesday = monday.plusDays(2);
        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveTypeId);
        reqDto.setStartDate(monday);
        reqDto.setEndDate(wednesday);
        reqDto.setReason("International technical symposium");
        reqDto.setRequiresDualApproval(true);

        HttpResponse<String> applyResp = post("/api/leave-requests", reqDto, employeeToken);
        assertEquals(201, applyResp.statusCode());
        Long leaveId = JsonPath.parse(applyResp.body()).read("$.id", Long.class);
        assertEquals("PENDING", JsonPath.parse(applyResp.body()).read("$.status", String.class));
        assertTrue(JsonPath.parse(applyResp.body()).read("$.requiresDualApproval", Boolean.class));

        // Step 1: Manager approves -> status becomes PARTIALLY_APPROVED
        HttpResponse<String> mgrApproveResp = patch("/api/leave-requests/" + leaveId + "/approve", null, managerToken);
        assertEquals(200, mgrApproveResp.statusCode());
        assertEquals("PARTIALLY_APPROVED", JsonPath.parse(mgrApproveResp.body()).read("$.status", String.class));
        assertEquals("APPROVED", JsonPath.parse(mgrApproveResp.body()).read("$.managerApprovalStatus", String.class));

        // Verify balance is NOT deducted yet (still 15 available)
        HttpResponse<String> balCheck1 = get("/api/leave-balances/me", employeeToken);
        assertEquals(200, balCheck1.statusCode());
        assertEquals(15, (int) JsonPath.parse(balCheck1.body()).read("$[0].available", Integer.class));

        // Step 2: HR approves -> status becomes APPROVED
        HttpResponse<String> hrApproveResp = patch("/api/leave-requests/" + leaveId + "/approve", null, hrToken);
        assertEquals(200, hrApproveResp.statusCode());
        assertEquals("APPROVED", JsonPath.parse(hrApproveResp.body()).read("$.status", String.class));
        assertEquals("APPROVED", JsonPath.parse(hrApproveResp.body()).read("$.hrApprovalStatus", String.class));

        // Verify balance is now deducted (12 available, 3 used)
        HttpResponse<String> balCheck2 = get("/api/leave-balances/me", employeeToken);
        assertEquals(200, balCheck2.statusCode());
        assertEquals(12, (int) JsonPath.parse(balCheck2.body()).read("$[0].available", Integer.class));
        assertEquals(3, (int) JsonPath.parse(balCheck2.body()).read("$[0].used", Integer.class));

        // Step 3: Check signoffs history endpoint over HTTP
        HttpResponse<String> signoffsResp = get("/api/leave-requests/" + leaveId + "/signoffs", managerToken);
        assertEquals(200, signoffsResp.statusCode());
        List<String> roles = JsonPath.parse(signoffsResp.body()).read("$[*].approverRole");
        assertTrue(roles.contains("MANAGER"));
        assertTrue(roles.contains("HR"));
    }

    @Test
    void testDualApprovalRejectionByManagerOverHttp() throws Exception {
        setupDepartmentAndStaff();

        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(3));
        int leaveYear = monday.getYear();

        LeaveBalanceRequestDTO balDto = new LeaveBalanceRequestDTO();
        balDto.setEmployeeId(employeeEmpId);
        balDto.setLeaveTypeId(leaveTypeId);
        balDto.setYear(leaveYear);
        balDto.setTotal(10);
        balDto.setAvailable(10);
        post("/api/leave-balances", balDto, adminToken);

        // Employee applies
        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveTypeId);
        reqDto.setStartDate(monday);
        reqDto.setEndDate(monday.plusDays(1));
        reqDto.setReason("External workshop");
        reqDto.setRequiresDualApproval(true);

        HttpResponse<String> applyResp = post("/api/leave-requests", reqDto, employeeToken);
        assertEquals(201, applyResp.statusCode());
        Long leaveId = JsonPath.parse(applyResp.body()).read("$.id", Long.class);

        // Manager rejects with reason
        HttpResponse<String> rejectResp = patch("/api/leave-requests/" + leaveId + "/reject?reason=Budget+freeze", null, managerToken);
        assertEquals(200, rejectResp.statusCode());
        assertEquals("REJECTED", JsonPath.parse(rejectResp.body()).read("$.status", String.class));
        assertEquals("Budget freeze", JsonPath.parse(rejectResp.body()).read("$.rejectionReason", String.class));

        // Balance remains unchanged (10 available, 0 used)
        HttpResponse<String> balResp = get("/api/leave-balances/me", employeeToken);
        assertEquals(10, (int) JsonPath.parse(balResp.body()).read("$[0].available", Integer.class));
        assertEquals(0, (int) JsonPath.parse(balResp.body()).read("$[0].used", Integer.class));
    }
}

