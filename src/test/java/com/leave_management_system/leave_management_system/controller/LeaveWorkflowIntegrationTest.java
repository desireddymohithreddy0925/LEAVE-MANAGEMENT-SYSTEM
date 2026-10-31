package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LeaveWorkflowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String adminToken;
    private String managerToken;
    private String hrToken;
    private String employeeToken;

    private User adminUser;
    private User managerUser;
    private User hrUser;
    private User employeeUser;

    private Employee managerEmp;
    private Employee hrEmp;
    private Employee employeeEmp;

    private Department department;
    private LeaveType leaveType;

    @BeforeEach
    public void setup() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // 1. Ensure essential permissions exist
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
        Permission pEmpCreate = getOrCreatePermission("EMPLOYEE_CREATE", "Create employee profiles");
        Permission pEmpUpdate = getOrCreatePermission("EMPLOYEE_UPDATE", "Update employee profiles");
        Permission pLeaveTypeManage = getOrCreatePermission("LEAVE_TYPE_MANAGE", "Manage leave types");
        Permission pLeaveTypeView = getOrCreatePermission("LEAVE_TYPE_VIEW", "View leave types");

        // 2. Roles
        Role adminRole = getOrCreateRole("ADMIN");
        adminRole.getPermissions().addAll(Set.of(
                pLeaveCreate, pLeaveApprove, pLeaveReject, pLeaveCancel,
                pLeaveViewTeam, pLeaveViewAll, pLeaveBalManage, pLeaveBalView,
                pDeptManage, pDeptView, pEmpView, pEmpCreate, pEmpUpdate,
                pLeaveTypeManage, pLeaveTypeView
        ));
        roleRepository.save(adminRole);

        Role managerRole = getOrCreateRole("MANAGER");
        managerRole.getPermissions().addAll(Set.of(
                pLeaveCreate, pLeaveApprove, pLeaveReject, pLeaveCancel,
                pLeaveViewTeam, pLeaveBalView, pDeptView, pEmpView
        ));
        roleRepository.save(managerRole);

        Role hrRole = getOrCreateRole("HR");
        hrRole.getPermissions().addAll(Set.of(
                pLeaveCreate, pLeaveApprove, pLeaveReject, pLeaveCancel,
                pLeaveViewAll, pLeaveBalManage, pLeaveBalView, pDeptView, pEmpView
        ));
        roleRepository.save(hrRole);

        Role employeeRole = getOrCreateRole("EMPLOYEE");
        employeeRole.getPermissions().addAll(Set.of(
                pLeaveCreate, pLeaveCancel, pLeaveBalView, pDeptView
        ));
        roleRepository.save(employeeRole);

        // 3. Admin User
        adminUser = createTestUser("admin.wf." + System.currentTimeMillis() + "@test.com", adminRole);
        adminToken = jwtUtils.generateTokenFromUsername(adminUser.getEmail());

        // 4. Department
        department = new Department();
        department.setName("Engineering WF " + System.currentTimeMillis());
        department = departmentRepository.save(department);

        // 5. Manager User & Employee
        managerUser = createTestUser("mgr.wf." + System.currentTimeMillis() + "@test.com", managerRole);
        managerToken = jwtUtils.generateTokenFromUsername(managerUser.getEmail());
        managerEmp = createEmployeeRecord(managerUser, department, null, "Mgr");
        department.setManager(managerEmp);
        department = departmentRepository.save(department);

        // 6. HR User & Employee
        hrUser = createTestUser("hr.wf." + System.currentTimeMillis() + "@test.com", hrRole);
        hrToken = jwtUtils.generateTokenFromUsername(hrUser.getEmail());
        hrEmp = createEmployeeRecord(hrUser, department, null, "HR");

        // 7. Subordinate Employee reporting to Manager
        employeeUser = createTestUser("emp.wf." + System.currentTimeMillis() + "@test.com", employeeRole);
        employeeToken = jwtUtils.generateTokenFromUsername(employeeUser.getEmail());
        employeeEmp = createEmployeeRecord(employeeUser, department, managerEmp, "Staff");

        // 8. Leave Type
        leaveType = new LeaveType();
        leaveType.setName("Paid Time Off " + System.currentTimeMillis());
        leaveType.setDefaultDays(20);
        leaveType.setActive(true);
        leaveType = leaveTypeRepository.save(leaveType);
    }

    private Permission getOrCreatePermission(String name, String desc) {
        return permissionRepository.findByName(name).orElseGet(() ->
                permissionRepository.save(new Permission(name, desc)));
    }

    private Role getOrCreateRole(String name) {
        return roleRepository.findByName(name).orElseGet(() ->
                roleRepository.save(new Role(name)));
    }

    private User createTestUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password123!"));
        user.setActive(true);
        user.setAccountNonLocked(true);
        user.setRoles(new HashSet<>(Set.of(role)));
        return userRepository.save(user);
    }

    private Employee createEmployeeRecord(User user, Department dept, Employee manager, String prefix) {
        Employee emp = new Employee();
        emp.setUser(user);
        emp.setFirstName(prefix + "First");
        emp.setLastName("Last");
        emp.setDepartment(dept);
        emp.setManager(manager);
        emp.setEmployeeCode("CODE-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000));
        emp.setStatus("ACTIVE");
        emp.setSalary(new BigDecimal("70000"));
        emp.setPhone("1234567890");
        return employeeRepository.save(emp);
    }

    private LocalDate getNextMonday(LocalDate from) {
        LocalDate d = from;
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    private LocalDate getNextSaturday(LocalDate from) {
        LocalDate d = from;
        while (d.getDayOfWeek() != DayOfWeek.SATURDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    void testEndToEndLeaveLifecycle_ApplyApproveDeductCancelRestoreReject() throws Exception {
        int currentYear = LocalDate.now().getYear();

        // 1. Admin allocates Leave Balance of 20 days for Employee
        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(2));
        int leaveYear = monday.getYear();

        LeaveBalanceRequestDTO balDto = new LeaveBalanceRequestDTO();
        balDto.setEmployeeId(employeeEmp.getId());
        balDto.setLeaveTypeId(leaveType.getId());
        balDto.setYear(leaveYear);
        balDto.setTotal(20);
        balDto.setAvailable(20);

        mockMvc.perform(post("/api/leave-balances")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(balDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(20))
                .andExpect(jsonPath("$.used").value(0));

        // 2. Employee queries own balances
        mockMvc.perform(get("/api/leave-balances/me")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].available").value(20));

        // 3. Employee checks prospective dates for overlap (Monday to Wednesday, 3 working days)
        LocalDate wednesday = monday.plusDays(2);

        mockMvc.perform(get("/api/leave-requests/check-overlap")
                .header("Authorization", "Bearer " + employeeToken)
                .param("startDate", monday.toString())
                .param("endDate", wednesday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapping").value(false));

        // 4. Employee submits Leave Request (Monday to Wednesday)
        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveType.getId());
        reqDto.setStartDate(monday);
        reqDto.setEndDate(wednesday);
        reqDto.setReason("Annual family vacation");

        MvcResult createResult = mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedDays").value(3))
                .andReturn();

        Long leaveRequestId = JsonPath.parse(createResult.getResponse().getContentAsString()).read("$.id", Long.class);

        // 5. Overlap prevention: Attempting overlapping leave while request is PENDING fails
        LeaveRequestDTO overlapDto = new LeaveRequestDTO();
        overlapDto.setLeaveTypeId(leaveType.getId());
        overlapDto.setStartDate(monday.plusDays(1)); // Tuesday to Thursday overlaps
        overlapDto.setEndDate(monday.plusDays(3));
        overlapDto.setReason("Conflicting request");

        mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overlapDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("overlaps with an existing pending or approved leave")));

        // 6. Manager views team leave requests and finds Employee's request
        mockMvc.perform(get("/api/managers/me/leave-requests")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(leaveRequestId.intValue())));

        // 7. Manager approves the leave request
        mockMvc.perform(patch("/api/leave-requests/" + leaveRequestId + "/approve")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(leaveRequestId))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 8. Employee verifies leave balance is deducted: available = 17, used = 3
        mockMvc.perform(get("/api/leave-balances/me")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(17))
                .andExpect(jsonPath("$[0].used").value(3));

        // 9. Employee cancels the approved leave request
        mockMvc.perform(patch("/api/leave-requests/" + leaveRequestId + "/cancel")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(leaveRequestId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 10. Employee verifies leave balance is restored: available = 20, used = 0
        mockMvc.perform(get("/api/leave-balances/me")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(20))
                .andExpect(jsonPath("$[0].used").value(0));

        // 11. Employee applies for a new leave request (2 working days: next week Mon to Tue)
        LocalDate nextMonday = monday.plusWeeks(1);
        LocalDate nextTuesday = nextMonday.plusDays(1);

        LeaveRequestDTO req2Dto = new LeaveRequestDTO();
        req2Dto.setLeaveTypeId(leaveType.getId());
        req2Dto.setStartDate(nextMonday);
        req2Dto.setEndDate(nextTuesday);
        req2Dto.setReason("Doctor visit appointment");

        MvcResult create2Result = mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2Dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        Long leave2Id = JsonPath.parse(create2Result.getResponse().getContentAsString()).read("$.id", Long.class);

        // 12. Manager rejects the leave request with a mandatory reason
        mockMvc.perform(patch("/api/leave-requests/" + leave2Id + "/reject")
                .header("Authorization", "Bearer " + managerToken)
                .param("reason", "Critical project sprint delivery during this period"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(leave2Id))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Critical project sprint delivery during this period"));

        // 13. Balance remains unchanged: available = 20, used = 0
        mockMvc.perform(get("/api/leave-balances/me")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(20))
                .andExpect(jsonPath("$[0].used").value(0));
    }

    @Test
    void testInsufficientLeaveBalance_BadRequest() throws Exception {
        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(2));
        LocalDate friday = monday.plusDays(4);

        // Allocate only 2 days of leave balance
        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(employeeEmp);
        balance.setLeaveType(leaveType);
        balance.setYear(monday.getYear());
        balance.setTotal(2);
        balance.setUsed(0);
        balance.setAvailable(2);
        leaveBalanceRepository.save(balance);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveType.getId());
        reqDto.setStartDate(monday);
        reqDto.setEndDate(friday);
        reqDto.setReason("Requesting more than available");

        mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient leave balance")));
    }

    @Test
    void testWeekendOnlyLeaveRequest_BadRequest() throws Exception {
        LocalDate saturday = getNextSaturday(LocalDate.now().plusWeeks(2));
        LocalDate sunday = saturday.plusDays(1);

        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(employeeEmp);
        balance.setLeaveType(leaveType);
        balance.setYear(saturday.getYear());
        balance.setTotal(10);
        balance.setUsed(0);
        balance.setAvailable(10);
        leaveBalanceRepository.save(balance);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveType.getId());
        reqDto.setStartDate(saturday);
        reqDto.setEndDate(sunday);
        reqDto.setReason("Weekend leave");

        mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Requested leave duration contains only weekends")));
    }

    @Test
    void testCrossYearLeaveRequest_BadRequest() throws Exception {
        int currentYear = LocalDate.now().getYear();
        LocalDate dec30 = LocalDate.of(currentYear, 12, 30);
        LocalDate jan02 = LocalDate.of(currentYear + 1, 1, 2);

        // Ensure dec30 is not in the past; if it is, shift by a year
        if (dec30.isBefore(LocalDate.now())) {
            dec30 = dec30.plusYears(1);
            jan02 = jan02.plusYears(1);
        }

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveType.getId());
        reqDto.setStartDate(dec30);
        reqDto.setEndDate(jan02);
        reqDto.setReason("New year vacation across year boundary");

        mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot span multiple calendar years")));
    }

    @Test
    void testRejectWithoutReason_BadRequest() throws Exception {
        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(2));
        LocalDate tuesday = monday.plusDays(1);

        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(employeeEmp);
        balance.setLeaveType(leaveType);
        balance.setYear(monday.getYear());
        balance.setTotal(10);
        balance.setUsed(0);
        balance.setAvailable(10);
        leaveBalanceRepository.save(balance);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveType.getId());
        reqDto.setStartDate(monday);
        reqDto.setEndDate(tuesday);
        reqDto.setReason("Short trip");

        MvcResult createResult = mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();

        Long leaveId = JsonPath.parse(createResult.getResponse().getContentAsString()).read("$.id", Long.class);

        // Reject with empty/null reason -> 400 Bad Request
        mockMvc.perform(patch("/api/leave-requests/" + leaveId + "/reject")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testManagerCannotApproveOwnLeaveRequest_Forbidden() throws Exception {
        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(2));
        LocalDate tuesday = monday.plusDays(1);

        // Manager has leave balance
        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(managerEmp);
        balance.setLeaveType(leaveType);
        balance.setYear(monday.getYear());
        balance.setTotal(10);
        balance.setUsed(0);
        balance.setAvailable(10);
        leaveBalanceRepository.save(balance);

        // Manager submits a leave request
        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveType.getId());
        reqDto.setStartDate(monday);
        reqDto.setEndDate(tuesday);
        reqDto.setReason("Manager personal time off");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();

        Long leaveId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Manager tries to approve their own request -> 403 Forbidden
        mockMvc.perform(patch("/api/leave-requests/" + leaveId + "/approve")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDualApprovalWorkflow_PartiallyApprovedThenFullyApproved() throws Exception {
        LocalDate monday = getNextMonday(LocalDate.now().plusWeeks(3));
        LocalDate wednesday = monday.plusDays(2); // 3 days

        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(employeeEmp);
        balance.setLeaveType(leaveType);
        balance.setYear(monday.getYear());
        balance.setTotal(15);
        balance.setUsed(0);
        balance.setAvailable(15);
        leaveBalanceRepository.save(balance);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setLeaveTypeId(leaveType.getId());
        reqDto.setStartDate(monday);
        reqDto.setEndDate(wednesday);
        reqDto.setReason("Conference abroad");
        reqDto.setRequiresDualApproval(true);

        MvcResult createResult = mockMvc.perform(post("/api/leave-requests")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiresDualApproval").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        Long leaveId = JsonPath.parse(createResult.getResponse().getContentAsString()).read("$.id", Long.class);

        // Step 1: Manager approves -> status becomes PARTIALLY_APPROVED
        mockMvc.perform(patch("/api/leave-requests/" + leaveId + "/approve")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_APPROVED"))
                .andExpect(jsonPath("$.managerApprovalStatus").value("APPROVED"));

        // Step 2: HR approves -> status becomes APPROVED, balance deducted
        mockMvc.perform(patch("/api/leave-requests/" + leaveId + "/approve")
                .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.hrApprovalStatus").value("APPROVED"));

        // Step 3: Verify balance deducted from 15 to 12
        mockMvc.perform(get("/api/leave-balances/me")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(12))
                .andExpect(jsonPath("$[0].used").value(3));

        // Step 4: Verify signoffs recorded
        mockMvc.perform(get("/api/leave-requests/" + leaveId + "/signoffs")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].approverRole", hasItems("MANAGER", "HR")));
    }
}
