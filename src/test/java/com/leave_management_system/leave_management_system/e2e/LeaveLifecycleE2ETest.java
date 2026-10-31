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

public class LeaveLifecycleE2ETest extends BaseE2ETest {

    private String adminToken;
    private String managerToken;
    private String employeeToken;

    private Long managerEmpId;
    private Long employeeEmpId;
    private Long departmentId;
    private Long leaveTypeId;

    @BeforeEach
    public void setupRolesAndPermissions() {
        // Ensure standard permissions exist
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

        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        employeeRole.getPermissions().addAll(Set.of(
                pLeaveCreate, pLeaveCancel, pLeaveBalView, pDeptView
        ));
        roleRepository.save(employeeRole);
    }

    @Test
    void testFullLeaveLifecycleOverHttp() throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());

        // 1. Register Admin and Login via HTTP
        String adminEmail = "admin.lifecycle." + ts + "@test.com";
        registerUserOverHttp(adminEmail, "AdminPass123!", "Admin", "User");
        elevateUserRole(adminEmail, "ADMIN");
        AuthResponseDTO adminAuth = loginUserOverHttp(adminEmail, "AdminPass123!");
        adminToken = adminAuth.getAccessToken();
        assertNotNull(adminToken);

        // 2. Admin creates Department over HTTP
        DepartmentRequestDTO deptDto = new DepartmentRequestDTO();
        deptDto.setName("Software Engineering " + ts);
        HttpResponse<String> deptResp = post("/api/departments", deptDto, adminToken);
        assertEquals(201, deptResp.statusCode());
        departmentId = JsonPath.parse(deptResp.body()).read("$.id", Long.class);
        assertNotNull(departmentId);

        // 3. Admin creates Leave Type over HTTP
        LeaveTypeRequestDTO ltDto = new LeaveTypeRequestDTO();
        ltDto.setName("Standard Vacation " + ts);
        ltDto.setDefaultDays(20);
        ltDto.setDescription("Paid annual leave");
        HttpResponse<String> ltResp = post("/api/leave-types", ltDto, adminToken);
        assertEquals(201, ltResp.statusCode());
        leaveTypeId = JsonPath.parse(ltResp.body()).read("$.id", Long.class);
        assertNotNull(leaveTypeId);

        // 4. Register Manager, elevate and login via HTTP
        String managerEmail = "mgr.lifecycle." + ts + "@test.com";
        registerUserOverHttp(managerEmail, "ManagerPass123!", "Team", "Manager");
        elevateUserRole(managerEmail, "MANAGER");
        AuthResponseDTO mgrAuth = loginUserOverHttp(managerEmail, "ManagerPass123!");
        managerToken = mgrAuth.getAccessToken();
        assertNotNull(managerToken);

        Employee mgrEmp = employeeRepository.findByUserEmail(managerEmail).orElseThrow();
        managerEmpId = mgrEmp.getId();

        // Admin assigns Manager to Department over HTTP
        HttpResponse<String> assignDeptMgrResp = put("/api/employees/" + managerEmpId + "/department?departmentId=" + departmentId, null, adminToken);
        assertEquals(200, assignDeptMgrResp.statusCode());

        HttpResponse<String> assignMgrResp = patch("/api/departments/" + departmentId + "/manager?managerId=" + managerEmpId, null, adminToken);
        assertEquals(200, assignMgrResp.statusCode());

        // 5. Register Employee and assign Manager & Department over HTTP
        String empEmail = "emp.lifecycle." + ts + "@test.com";
        registerUserOverHttp(empEmail, "EmpPass123!", "Staff", "Engineer");
        AuthResponseDTO empAuth = loginUserOverHttp(empEmail, "EmpPass123!");
        employeeToken = empAuth.getAccessToken();
        assertNotNull(employeeToken);

        Employee emp = employeeRepository.findByUserEmail(empEmail).orElseThrow();
        employeeEmpId = emp.getId();

        // Assign Employee's department and manager over HTTP
        HttpResponse<String> assignEmpDeptResp = put("/api/employees/" + employeeEmpId + "/department?departmentId=" + departmentId, null, adminToken);
        assertEquals(200, assignEmpDeptResp.statusCode());

        HttpResponse<String> assignEmpMgrResp = patch("/api/employees/" + employeeEmpId + "/manager?managerId=" + managerEmpId, null, adminToken);
        assertEquals(200, assignEmpMgrResp.statusCode());

        // 6. Admin allocates 20 days Leave Balance for Employee over HTTP
        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(2));
        int leaveYear = monday.getYear();

        LeaveBalanceRequestDTO balDto = new LeaveBalanceRequestDTO();
        balDto.setEmployeeId(employeeEmpId);
        balDto.setLeaveTypeId(leaveTypeId);
        balDto.setYear(leaveYear);
        balDto.setTotal(20);
        balDto.setAvailable(20);

        HttpResponse<String> balResp = post("/api/leave-balances", balDto, adminToken);
        assertEquals(201, balResp.statusCode());
        assertEquals(20, (int) JsonPath.parse(balResp.body()).read("$.available", Integer.class));
        assertEquals(0, (int) JsonPath.parse(balResp.body()).read("$.used", Integer.class));

        // 7. Employee checks own balances over HTTP
        HttpResponse<String> myBalResp = get("/api/leave-balances/me", employeeToken);
        assertEquals(200, myBalResp.statusCode());
        assertEquals(20, (int) JsonPath.parse(myBalResp.body()).read("$[0].available", Integer.class));

        // 8. Employee checks prospective leave overlap over HTTP (Monday to Wednesday, 3 days)
        LocalDate wednesday = monday.plusDays(2);
        HttpResponse<String> overlapResp = get("/api/leave-requests/check-overlap?startDate=" + monday + "&endDate=" + wednesday, employeeToken);
        assertEquals(200, overlapResp.statusCode());
        assertFalse(JsonPath.parse(overlapResp.body()).read("$.overlapping", Boolean.class));

        // 9. Employee applies for 3-day leave over HTTP
        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveTypeId);
        reqDto.setStartDate(monday);
        reqDto.setEndDate(wednesday);
        reqDto.setReason("Annual family trip");

        HttpResponse<String> reqResp = post("/api/leave-requests", reqDto, employeeToken);
        assertEquals(201, reqResp.statusCode());
        Long leaveRequestId = JsonPath.parse(reqResp.body()).read("$.id", Long.class);
        assertEquals("PENDING", JsonPath.parse(reqResp.body()).read("$.status", String.class));
        assertEquals(3, (int) JsonPath.parse(reqResp.body()).read("$.requestedDays", Integer.class));

        // 10. Overlap prevention: Attempting overlapping leave over HTTP fails with 400
        LeaveRequestDTO overlapDto = new LeaveRequestDTO();
        overlapDto.setLeaveTypeId(leaveTypeId);
        overlapDto.setStartDate(monday.plusDays(1)); // Tuesday to Thursday overlaps
        overlapDto.setEndDate(monday.plusDays(3));
        overlapDto.setReason("Conflicting dates");

        HttpResponse<String> badOverlapResp = post("/api/leave-requests", overlapDto, employeeToken);
        assertEquals(400, badOverlapResp.statusCode());
        assertTrue(badOverlapResp.body().contains("overlaps with an existing pending or approved leave"));

        // 11. Manager reviews team leave requests over HTTP
        HttpResponse<String> teamReqsResp = get("/api/managers/me/leave-requests", managerToken);
        assertEquals(200, teamReqsResp.statusCode());
        List<Integer> reqIds = JsonPath.parse(teamReqsResp.body()).read("$[*].id");
        assertTrue(reqIds.contains(leaveRequestId.intValue()));

        // 12. Manager approves leave request over HTTP
        HttpResponse<String> approveResp = patch("/api/leave-requests/" + leaveRequestId + "/approve", null, managerToken);
        assertEquals(200, approveResp.statusCode());
        assertEquals("APPROVED", JsonPath.parse(approveResp.body()).read("$.status", String.class));

        // 13. Employee verifies balance deducted (available: 17, used: 3) over HTTP
        HttpResponse<String> balAfterApprove = get("/api/leave-balances/me", employeeToken);
        assertEquals(200, balAfterApprove.statusCode());
        assertEquals(17, (int) JsonPath.parse(balAfterApprove.body()).read("$[0].available", Integer.class));
        assertEquals(3, (int) JsonPath.parse(balAfterApprove.body()).read("$[0].used", Integer.class));

        // 14. Employee cancels the approved leave request over HTTP
        HttpResponse<String> cancelResp = patch("/api/leave-requests/" + leaveRequestId + "/cancel", null, employeeToken);
        assertEquals(200, cancelResp.statusCode());
        assertEquals("CANCELLED", JsonPath.parse(cancelResp.body()).read("$.status", String.class));

        // 15. Employee verifies balance restored (available: 20, used: 0) over HTTP
        HttpResponse<String> balAfterCancel = get("/api/leave-balances/me", employeeToken);
        assertEquals(200, balAfterCancel.statusCode());
        assertEquals(20, (int) JsonPath.parse(balAfterCancel.body()).read("$[0].available", Integer.class));
        assertEquals(0, (int) JsonPath.parse(balAfterCancel.body()).read("$[0].used", Integer.class));

        // 16. Employee reapplies for different dates over HTTP (2 days: next week Mon to Tue)
        LocalDate nextMon = monday.plusWeeks(1);
        LocalDate nextTue = nextMon.plusDays(1);

        LeaveRequestDTO req2Dto = new LeaveRequestDTO();
        req2Dto.setLeaveTypeId(leaveTypeId);
        req2Dto.setStartDate(nextMon);
        req2Dto.setEndDate(nextTue);
        req2Dto.setReason("Personal dental surgery");

        HttpResponse<String> req2Resp = post("/api/leave-requests", req2Dto, employeeToken);
        assertEquals(201, req2Resp.statusCode());
        Long leave2Id = JsonPath.parse(req2Resp.body()).read("$.id", Long.class);

        // 17. Manager rejects with mandatory reason over HTTP
        HttpResponse<String> rejectResp = patch("/api/leave-requests/" + leave2Id + "/reject?reason=Critical+product+deployment", null, managerToken);
        assertEquals(200, rejectResp.statusCode());
        assertEquals("REJECTED", JsonPath.parse(rejectResp.body()).read("$.status", String.class));
        assertEquals("Critical product deployment", JsonPath.parse(rejectResp.body()).read("$.rejectionReason", String.class));

        // 18. Balance remains unchanged (available: 20, used: 0) over HTTP
        HttpResponse<String> balAfterReject = get("/api/leave-balances/me", employeeToken);
        assertEquals(200, balAfterReject.statusCode());
        assertEquals(20, (int) JsonPath.parse(balAfterReject.body()).read("$[0].available", Integer.class));
        assertEquals(0, (int) JsonPath.parse(balAfterReject.body()).read("$[0].used", Integer.class));
    }

    @Test
    void testLeaveRequestValidationsOverHttp() throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());

        // Setup Admin and Employee
        String adminEmail = "admin.val." + ts + "@test.com";
        registerUserOverHttp(adminEmail, "AdminPass123!", "ValAdmin", "User");
        elevateUserRole(adminEmail, "ADMIN");
        adminToken = loginUserOverHttp(adminEmail, "AdminPass123!").getAccessToken();

        String empEmail = "emp.val." + ts + "@test.com";
        registerUserOverHttp(empEmail, "EmpPass123!", "ValStaff", "User");
        employeeToken = loginUserOverHttp(empEmail, "EmpPass123!").getAccessToken();
        Employee emp = employeeRepository.findByUserEmail(empEmail).orElseThrow();

        // Create Leave Type
        LeaveTypeRequestDTO ltDto = new LeaveTypeRequestDTO();
        ltDto.setName("Val Vacation " + ts);
        ltDto.setDefaultDays(10);
        HttpResponse<String> ltResp = post("/api/leave-types", ltDto, adminToken);
        Long ltId = JsonPath.parse(ltResp.body()).read("$.id", Long.class);

        // Allocate only 2 days of balance
        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(2));
        LeaveBalanceRequestDTO balDto = new LeaveBalanceRequestDTO();
        balDto.setEmployeeId(emp.getId());
        balDto.setLeaveTypeId(ltId);
        balDto.setYear(monday.getYear());
        balDto.setTotal(2);
        balDto.setAvailable(2);
        post("/api/leave-balances", balDto, adminToken);

        // 1. Insufficient balance (requesting 5 days Mon-Fri with only 2 days available)
        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(ltId);
        reqDto.setStartDate(monday);
        reqDto.setEndDate(monday.plusDays(4));
        reqDto.setReason("Excessive request");

        HttpResponse<String> insufficientResp = post("/api/leave-requests", reqDto, employeeToken);
        assertEquals(400, insufficientResp.statusCode());
        assertTrue(insufficientResp.body().contains("Insufficient leave balance"));

        // 2. Weekend-only duration
        LocalDate saturday = getNextSaturday(LocalDate.now().plusWeeks(2));
        LeaveRequestDTO weekendDto = new LeaveRequestDTO();
        weekendDto.setLeaveTypeId(ltId);
        weekendDto.setStartDate(saturday);
        weekendDto.setEndDate(saturday.plusDays(1));
        weekendDto.setReason("Weekend only");

        HttpResponse<String> weekendResp = post("/api/leave-requests", weekendDto, employeeToken);
        assertEquals(400, weekendResp.statusCode());
        assertTrue(weekendResp.body().contains("Requested leave duration contains only weekends"));
    }
}
