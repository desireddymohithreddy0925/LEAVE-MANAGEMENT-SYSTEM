package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "req.test@example.com", authorities = {"ROLE_ADMIN", "DEPARTMENT_MANAGE", "DEPARTMENT_VIEW", "EMPLOYEE_CREATE", "EMPLOYEE_VIEW", "EMPLOYEE_UPDATE", "EMPLOYEE_DEACTIVATE", "LEAVE_BALANCE_MANAGE", "LEAVE_BALANCE_VIEW", "LEAVE_CREATE", "LEAVE_VIEW_ALL", "LEAVE_TYPE_MANAGE", "ROLE_PERMISSION_MANAGE", "LEAVE_APPROVE", "LEAVE_REJECT", "LEAVE_CANCEL"})
public class LeaveRequestControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.EmployeeRepository employeeRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.LeaveTypeRepository leaveTypeRepository;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();
    }

    private Long createDepartment() throws Exception {
        com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO dto = new com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO();
        dto.setName("Req Dept " + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000));
        MvcResult result = mockMvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Long createEmployee() throws Exception {
        Long deptId = createDepartment();
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("Req");
        dto.setLastName("Test");
        dto.setEmail("req.test@example.com");
        dto.setPhone("1234567890");
        dto.setDepartmentId(deptId);
        dto.setEmployeeCode("EMP-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000));
        dto.setStatus("ACTIVE");
        dto.setSalary(new java.math.BigDecimal("50000"));
        MvcResult result = mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Long createEmployeeWithEmail(String email, Long deptId) throws Exception {
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("Emp");
        dto.setLastName("Test");
        dto.setEmail(email);
        dto.setPhone("1234567890");
        dto.setDepartmentId(deptId);
        dto.setEmployeeCode("EMP-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000));
        dto.setStatus("ACTIVE");
        dto.setSalary(new java.math.BigDecimal("50000"));
        MvcResult result = mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Long createLeaveType() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();
        dto.setName("Req Leave " + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000));
        dto.setDescription("Test");
        dto.setDefaultDays(12);
        MvcResult result = mockMvc.perform(post("/api/leave-types").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private void createLeaveBalance(Long empId, Long typeId) throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(empId);
        dto.setLeaveTypeId(typeId);
        dto.setAvailable(20);
        mockMvc.perform(post("/api/leave-balances").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)));
    }

    private void createLeaveBalanceWithDays(Long empId, Long typeId, int year, int totalDays) throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(empId);
        dto.setLeaveTypeId(typeId);
        dto.setYear(year);
        dto.setTotal(totalDays);
        mockMvc.perform(post("/api/leave-balances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    private LocalDate[] getSafeWeekdayRange(int daysFromNow, int durationDays) {
        LocalDate start = LocalDate.now().plusDays(daysFromNow);
        if (start.getYear() > LocalDate.now().getYear() || (start.getMonthValue() == 12 && start.getDayOfMonth() >= 25)) {
            start = LocalDate.of(LocalDate.now().getYear() + 1, 3, 10);
        }
        while (start.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || start.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            start = start.plusDays(1);
        }
        LocalDate end = start;
        int added = 1;
        while (added < durationDays) {
            end = end.plusDays(1);
            if (end.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && end.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return new LocalDate[]{start, end};
    }

    private LocalDate[] getUpcomingWeekendRange() {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() != java.time.DayOfWeek.SATURDAY) {
            date = date.plusDays(1);
        }
        if (date.getMonthValue() == 12 && date.getDayOfMonth() >= 30) {
            date = LocalDate.of(date.getYear() + 1, 1, 9);
            while (date.getDayOfWeek() != java.time.DayOfWeek.SATURDAY) {
                date = date.plusDays(1);
            }
        }
        return new LocalDate[]{date, date.plusDays(1)};
    }

    @Test
    void searchLeaveRequests_Pagination() throws Exception {
        mockMvc.perform(get("/api/leave-requests")
                .param("page", "0")
                .param("size", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void createLeaveRequest_InvalidDates_Throws400() throws Exception {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveTypeId(1L);
        dto.setStartDate(LocalDate.now().minusDays(1)); // Past date
        dto.setEndDate(LocalDate.now().plusDays(2));
        dto.setReason("Vacation");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullLeaveRequestWorkflow_Success() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        createLeaveBalance(empId, typeId);

        LocalDate[] range = getSafeWeekdayRange(10, 3);
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(empId);
        dto.setLeaveTypeId(typeId);
        dto.setStartDate(range[0]);
        dto.setEndDate(range[1]);
        dto.setReason("Workflow Test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        mockMvc.perform(put("/api/leave-requests/" + reqId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void createLeaveRequest_DifferentYearBalanceExists_MissingApplicableYearBalance_Returns404() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();

        int currentYear = LocalDate.now().getYear();
        LeaveBalanceRequestDTO balDto = new LeaveBalanceRequestDTO();
        balDto.setEmployeeId(empId);
        balDto.setLeaveTypeId(typeId);
        balDto.setYear(currentYear);
        balDto.setTotal(20);
        mockMvc.perform(post("/api/leave-balances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(balDto)))
                .andExpect(status().isCreated());

        int nextYear = currentYear + 1;
        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(LocalDate.of(nextYear, 6, 1));
        reqDto.setEndDate(LocalDate.of(nextYear, 6, 4));
        reqDto.setReason("Regression Test: Applicable year balance missing");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString(String.valueOf(nextYear))));
    }

    @Test
    void createLeaveRequest_CrossCalendarYearDates_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();

        int targetYear = LocalDate.now().getYear() + 1;
        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(LocalDate.of(targetYear, 12, 28));
        reqDto.setEndDate(LocalDate.of(targetYear + 1, 1, 4));
        reqDto.setReason("Cross year leave request");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("multiple calendar years")));
    }

    @Test
    void createLeaveRequest_SelfService_AuthenticatedEmployeeWithoutEmployeeId_Success() throws Exception {
        Long deptId = createDepartment();
        String empEmail = "selfservice." + System.currentTimeMillis() + "@example.com";
        Long empId = createEmployeeWithEmail(empEmail, deptId);
        Long typeId = createLeaveType();

        LocalDate[] range = getSafeWeekdayRange(12, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 15);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(null);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Self-service leave application");

        mockMvc.perform(post("/api/leave-requests")
                .with(user(empEmail).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("LEAVE_CREATE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee.id").value(empId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createLeaveRequest_SelfService_SpoofEmployeeId_OverriddenToAuthenticatedEmployee() throws Exception {
        Long deptId = createDepartment();
        String callerEmail = "caller." + System.currentTimeMillis() + "@example.com";
        Long callerEmpId = createEmployeeWithEmail(callerEmail, deptId);

        String victimEmail = "victim." + System.currentTimeMillis() + "@example.com";
        Long victimEmpId = createEmployeeWithEmail(victimEmail, deptId);

        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(16, 2);
        createLeaveBalanceWithDays(callerEmpId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(victimEmpId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Spoofing attempt");

        mockMvc.perform(post("/api/leave-requests")
                .with(user(callerEmail).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("LEAVE_CREATE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee.id").value(callerEmpId));
    }

    @Test
    void createLeaveRequest_InsufficientBalance_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(20, 5);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 2);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Too many days requested");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Insufficient leave balance")));
    }

    @Test
    void createLeaveRequest_OverlappingRequests_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(28, 4);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 20);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("First request");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated());

        LeaveRequestDTO overlapDto = new LeaveRequestDTO();
        overlapDto.setEmployeeId(empId);
        overlapDto.setLeaveTypeId(typeId);
        overlapDto.setStartDate(range[0]);
        overlapDto.setEndDate(range[1]);
        overlapDto.setReason("Overlapping request");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overlapDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("overlaps")));
    }

    @Test
    void createLeaveRequest_WeekendOnlyRange_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] weekend = getUpcomingWeekendRange();
        createLeaveBalanceWithDays(empId, typeId, weekend[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(weekend[0]);
        reqDto.setEndDate(weekend[1]);
        reqDto.setReason("Weekend only");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("weekends")));
    }

    @Test
    void createLeaveRequest_InactiveLeaveType_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();

        LocalDate[] range = getSafeWeekdayRange(35, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        mockMvc.perform(put("/api/leave-types/" + typeId + "/deactivate"))
                .andExpect(status().isOk());

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Applying on inactive type");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not active")));
    }

    @Test
    void rejectLeaveRequest_MandatoryReason_Success() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(42, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Will be rejected");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Reject without reason fails (400)
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/reject"))
                .andExpect(status().isBadRequest());

        // Reject with reason succeeds via PATCH
        mockMvc.perform(patch("/api/leave-requests/" + reqId + "/reject")
                .param("reason", "Project deadline conflicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Project deadline conflicts"));
    }

    @Test
    void cancelLeaveRequest_ApprovedLeave_RestoresBalance_AndDoubleCancelFails() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(50, 3);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 15);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("To be approved then cancelled");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Approve
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Check balance deducted
        mockMvc.perform(get("/api/leave-balances/employee/" + empId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(12))
                .andExpect(jsonPath("$[0].used").value(3));

        // Cancel via PATCH
        mockMvc.perform(patch("/api/leave-requests/" + reqId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Check balance restored
        mockMvc.perform(get("/api/leave-balances/employee/" + empId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(15))
                .andExpect(jsonPath("$[0].used").value(0));

        // Double cancellation attempt via PUT fails
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("already cancelled")));
    }

    @Test
    void getMyLeaveRequests_ReturnsOnlyOwnLeaves_WithStatusAndYearFilter() throws Exception {
        Long deptId = createDepartment();
        String emp1Email = "emp1." + System.currentTimeMillis() + "@example.com";
        Long emp1Id = createEmployeeWithEmail(emp1Email, deptId);

        String emp2Email = "emp2." + System.currentTimeMillis() + "@example.com";
        Long emp2Id = createEmployeeWithEmail(emp2Email, deptId);

        Long typeId = createLeaveType();
        LocalDate[] range1 = getSafeWeekdayRange(58, 2);
        LocalDate[] range2 = getSafeWeekdayRange(65, 2);
        createLeaveBalanceWithDays(emp1Id, typeId, range1[0].getYear(), 10);
        createLeaveBalanceWithDays(emp2Id, typeId, range2[0].getYear(), 10);

        // Emp1 applies leave
        LeaveRequestDTO req1 = new LeaveRequestDTO();
        req1.setLeaveTypeId(typeId);
        req1.setStartDate(range1[0]);
        req1.setEndDate(range1[1]);
        req1.setReason("Emp1 Vacation");

        mockMvc.perform(post("/api/leave-requests")
                .with(user(emp1Email).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("LEAVE_CREATE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Emp2 applies leave
        LeaveRequestDTO req2 = new LeaveRequestDTO();
        req2.setLeaveTypeId(typeId);
        req2.setStartDate(range2[0]);
        req2.setEndDate(range2[1]);
        req2.setReason("Emp2 Vacation");

        mockMvc.perform(post("/api/leave-requests")
                .with(user(emp2Email).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("LEAVE_CREATE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // Call /api/leave-requests/me as Emp1
        mockMvc.perform(get("/api/leave-requests/me")
                .with(user(emp1Email).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].employee.id").value(emp1Id))
                .andExpect(jsonPath("$.content[0].reason").value("Emp1 Vacation"));

        // Filter /api/leave-requests/me by status=APPROVED (should be empty since it is PENDING)
        mockMvc.perform(get("/api/leave-requests/me")
                .with(user(emp1Email).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        // Filter /api/leave-requests/me by year
        mockMvc.perform(get("/api/leave-requests/me")
                .with(user(emp1Email).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                .param("year", String.valueOf(range1[0].getYear())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void searchLeaveRequests_WithDepartmentFilter_ReturnsMatchingDepartmentOnly() throws Exception {
        Long dept1Id = createDepartment();
        Long dept2Id = createDepartment();

        String emp1Email = "dept1." + System.currentTimeMillis() + "@example.com";
        Long emp1Id = createEmployeeWithEmail(emp1Email, dept1Id);

        String emp2Email = "dept2." + System.currentTimeMillis() + "@example.com";
        Long emp2Id = createEmployeeWithEmail(emp2Email, dept2Id);

        Long typeId = createLeaveType();
        LocalDate[] range1 = getSafeWeekdayRange(72, 2);
        LocalDate[] range2 = getSafeWeekdayRange(80, 2);
        createLeaveBalanceWithDays(emp1Id, typeId, range1[0].getYear(), 10);
        createLeaveBalanceWithDays(emp2Id, typeId, range2[0].getYear(), 10);

        LeaveRequestDTO req1 = new LeaveRequestDTO();
        req1.setEmployeeId(emp1Id);
        req1.setLeaveTypeId(typeId);
        req1.setStartDate(range1[0]);
        req1.setEndDate(range1[1]);
        req1.setReason("Dept 1 leave");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        LeaveRequestDTO req2 = new LeaveRequestDTO();
        req2.setEmployeeId(emp2Id);
        req2.setLeaveTypeId(typeId);
        req2.setStartDate(range2[0]);
        req2.setEndDate(range2[1]);
        req2.setReason("Dept 2 leave");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // Search with departmentId = dept1Id
        mockMvc.perform(get("/api/leave-requests")
                .param("departmentId", String.valueOf(dept1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].employee.id").value(emp1Id));
    }

    @Test
    void cancelLeaveRequest_UnauthorizedUser_Returns403Forbidden() throws Exception {
        Long deptId = createDepartment();
        String ownerEmail = "owner." + System.currentTimeMillis() + "@example.com";
        Long ownerEmpId = createEmployeeWithEmail(ownerEmail, deptId);

        String strangerEmail = "stranger." + System.currentTimeMillis() + "@example.com";
        createEmployeeWithEmail(strangerEmail, deptId);

        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(88, 2);
        createLeaveBalanceWithDays(ownerEmpId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(ownerEmpId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Owner leave");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Stranger tries to cancel Owner's leave -> 403 Forbidden
        mockMvc.perform(patch("/api/leave-requests/" + reqId + "/cancel")
                .with(user(strangerEmail).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("LEAVE_CANCEL"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkOverlap_WithOverlappingDates_Returns200WithOverlapTrue() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(94, 3);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Existing leave");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated());

        // Check overlap with identical dates
        mockMvc.perform(get("/api/leave-requests/check-overlap")
                .param("employeeId", String.valueOf(empId))
                .param("startDate", range[0].toString())
                .param("endDate", range[1].toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapping").value(true))
                .andExpect(jsonPath("$.conflictingRequests.length()").value(1))
                .andExpect(jsonPath("$.conflictingRequests[0].startDate").value(range[0].toString()));
    }

    @Test
    void checkOverlap_WithNonOverlappingDates_Returns200WithOverlapFalse() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(98, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        // No leaves created for this employee yet on these dates
        mockMvc.perform(get("/api/leave-requests/check-overlap")
                .param("employeeId", String.valueOf(empId))
                .param("startDate", range[0].toString())
                .param("endDate", range[1].toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapping").value(false))
                .andExpect(jsonPath("$.conflictingRequests.length()").value(0))
                .andExpect(jsonPath("$.message").value("No overlapping leave requests found"));
    }

    @Test
    void checkOverlap_InvalidDates_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();

        mockMvc.perform(get("/api/leave-requests/check-overlap")
                .param("employeeId", String.valueOf(empId))
                .param("startDate", "2026-09-20")
                .param("endDate", "2026-09-10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLeaveRequest_DifferentLeaveTypeOverlapping_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long type1Id = createLeaveType();
        Long type2Id = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(102, 4); // 4 weekdays (e.g. Mon - Thu)
        createLeaveBalanceWithDays(empId, type1Id, range[0].getYear(), 10);
        createLeaveBalanceWithDays(empId, type2Id, range[0].getYear(), 10);

        // Apply with Type 1 for Mon - Wed
        LeaveRequestDTO req1 = new LeaveRequestDTO();
        req1.setEmployeeId(empId);
        req1.setLeaveTypeId(type1Id);
        req1.setStartDate(range[0]);
        req1.setEndDate(range[0].plusDays(2));
        req1.setReason("Annual leave");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Apply with Type 2 for Tue - Thu (overlaps on Tue & Wed)
        LeaveRequestDTO req2 = new LeaveRequestDTO();
        req2.setEmployeeId(empId);
        req2.setLeaveTypeId(type2Id);
        req2.setStartDate(range[0].plusDays(1));
        req2.setEndDate(range[0].plusDays(3));
        req2.setReason("Sick leave on overlapping days");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("overlaps with an existing pending or approved leave")));
    }

    @Test
    void createLeaveRequest_AdjacentDates_SuccessNoOverlap() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(108, 4); // 4 weekdays (e.g. Mon - Thu)
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        // Leave 1: Mon - Tue
        LeaveRequestDTO req1 = new LeaveRequestDTO();
        req1.setEmployeeId(empId);
        req1.setLeaveTypeId(typeId);
        req1.setStartDate(range[0]);
        req1.setEndDate(range[0].plusDays(1));
        req1.setReason("Mon-Tue");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Leave 2: Wed - Thu (immediately adjacent, not overlapping)
        LeaveRequestDTO req2 = new LeaveRequestDTO();
        req2.setEmployeeId(empId);
        req2.setLeaveTypeId(typeId);
        req2.setStartDate(range[0].plusDays(2));
        req2.setEndDate(range[0].plusDays(3));
        req2.setReason("Wed-Thu");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());
    }

    @Test
    void approveLeaveRequest_ViaPatch_Success() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(120, 3);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("To be approved via PATCH");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Approve via PATCH
        mockMvc.perform(patch("/api/leave-requests/" + reqId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reqId))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approveLeaveRequest_DoubleApproval_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(126, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Double approval check");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // First approval succeeds
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Second approval attempt fails with 400 Bad Request
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/approve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Only pending requests can be approved")));
    }

    @Test
    void approveLeaveRequest_AlreadyRejected_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(132, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Reject then approve check");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Reject
        mockMvc.perform(patch("/api/leave-requests/" + reqId + "/reject")
                .param("reason", "Budget constraints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Attempt approve on rejected request -> 400 Bad Request
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/approve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Only pending requests can be approved")));
    }

    @Test
    void approveLeaveRequest_AlreadyCancelled_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(138, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Cancel then approve check");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Cancel
        mockMvc.perform(patch("/api/leave-requests/" + reqId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Attempt approve on cancelled request -> 400 Bad Request
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/approve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Only pending requests can be approved")));
    }

    @Test
    void approveLeaveRequest_OverlappingApprovedLeaveAlreadyExists_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(144, 4); // 4 weekdays
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        // Request 1: days 0 to 2 (Mon - Wed)
        LeaveRequestDTO req1 = new LeaveRequestDTO();
        req1.setEmployeeId(empId);
        req1.setLeaveTypeId(typeId);
        req1.setStartDate(range[0]);
        req1.setEndDate(range[0].plusDays(2));
        req1.setReason("First request");

        MvcResult res1 = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn();
        Long req1Id = JsonPath.parse(res1.getResponse().getContentAsString()).read("$.id", Long.class);

        // Approve Request 1
        mockMvc.perform(put("/api/leave-requests/" + req1Id + "/approve"))
                .andExpect(status().isOk());

        // Create Request 2 directly as PENDING in repository on overlapping dates (days 1 to 3: Tue - Thu)
        com.leave_management_system.leave_management_system.entity.Employee employee = employeeRepository.findById(empId).get();
        com.leave_management_system.leave_management_system.entity.LeaveType leaveType = leaveTypeRepository.findById(typeId).get();
        com.leave_management_system.leave_management_system.entity.LeaveRequest req2 = new com.leave_management_system.leave_management_system.entity.LeaveRequest();
        req2.setEmployee(employee);
        req2.setLeaveType(leaveType);
        req2.setStartDate(range[0].plusDays(1));
        req2.setEndDate(range[0].plusDays(3));
        req2.setStatus(com.leave_management_system.leave_management_system.entity.LeaveStatus.PENDING);
        req2.setReason("Second request overlapping with first approved");
        req2 = leaveRequestRepository.save(req2);

        // Attempt to approve Request 2 -> fails because Request 1 is already approved for overlapping dates
        mockMvc.perform(put("/api/leave-requests/" + req2.getId() + "/approve"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("an overlapping leave request is already approved for this employee")));
    }

    @Test
    void approveLeaveRequest_UnauthorizedUser_Returns403Forbidden() throws Exception {
        Long deptId = createDepartment();
        String regularEmpEmail = "regular.emp." + System.currentTimeMillis() + "@example.com";
        Long empId = createEmployeeWithEmail(regularEmpEmail, deptId);
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(152, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Regular emp request");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Regular employee without LEAVE_APPROVE authority attempts to approve -> 403 Forbidden
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/approve")
                .with(user(regularEmpEmail).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveLeaveRequest_NotFound_Returns404NotFound() throws Exception {
        mockMvc.perform(put("/api/leave-requests/999999/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectLeaveRequest_ViaPostWithJsonBody_Success() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(160, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("To reject via POST body");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        com.leave_management_system.leave_management_system.dto.RejectRequestDTO rejectDto = new com.leave_management_system.leave_management_system.dto.RejectRequestDTO();
        rejectDto.setReason("Critical project deliverable conflict");

        mockMvc.perform(post("/api/leave-requests/" + reqId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rejectDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Critical project deliverable conflict"));
    }

    @Test
    void rejectLeaveRequest_DoubleRejection_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(165, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Double rejection test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // First rejection -> 200 OK
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/reject")
                .param("reason", "First rejection"))
                .andExpect(status().isOk());

        // Second rejection -> 400 Bad Request
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/reject")
                .param("reason", "Second rejection attempt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Only pending requests can be rejected")));
    }

    @Test
    void rejectLeaveRequest_AlreadyApproved_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(170, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Approved to reject test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Approve
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/approve"))
                .andExpect(status().isOk());

        // Attempt reject on approved leave -> 400 Bad Request
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/reject")
                .param("reason", "Attempt to reject approved"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Only pending requests can be rejected")));
    }

    @Test
    void rejectLeaveRequest_AlreadyCancelled_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(175, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Cancelled to reject test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Cancel
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/cancel"))
                .andExpect(status().isOk());

        // Attempt reject on cancelled leave -> 400 Bad Request
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/reject")
                .param("reason", "Attempt to reject cancelled"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Only pending requests can be rejected")));
    }

    @Test
    void rejectLeaveRequest_BlankReasonInBody_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(180, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Blank reason test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        com.leave_management_system.leave_management_system.dto.RejectRequestDTO rejectDto = new com.leave_management_system.leave_management_system.dto.RejectRequestDTO();
        rejectDto.setReason("   ");

        mockMvc.perform(post("/api/leave-requests/" + reqId + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rejectDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectLeaveRequest_ReasonExceeds500Characters_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(185, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Overlong reason test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        String tooLong = "R".repeat(501);
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/reject")
                .param("reason", tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("must not exceed 500 characters")));
    }

    @Test
    void rejectLeaveRequest_UnauthorizedUser_Returns403Forbidden() throws Exception {
        Long deptId = createDepartment();
        String regularEmpEmail = "reject.emp." + System.currentTimeMillis() + "@example.com";
        Long empId = createEmployeeWithEmail(regularEmpEmail, deptId);
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(190, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Regular emp reject test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Regular employee without LEAVE_REJECT authority -> 403 Forbidden
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/reject")
                .param("reason", "Unauthorized reject attempt")
                .with(user(regularEmpEmail).authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectLeaveRequest_NotFound_Returns404NotFound() throws Exception {
        mockMvc.perform(put("/api/leave-requests/999999/reject")
                .param("reason", "Non-existent request"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectLeaveRequest_LeavesBalanceUntouched() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(195, 3);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 12);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Balance preservation test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Reject request
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/reject")
                .param("reason", "Denial with preserved balance"))
                .andExpect(status().isOk());

        // Verify leave balance has available=12 and used=0 (untouched)
        mockMvc.perform(get("/api/leave-balances/employee/" + empId)
                .param("year", String.valueOf(range[0].getYear())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(12))
                .andExpect(jsonPath("$[0].used").value(0));
    }

    @Test
    void rejectLeaveRequest_ReapplyOnSameDatesAfterRejection_Success() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(200, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Initial request to be rejected");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Reject initial request
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/reject")
                .param("reason", "Initial denial"))
                .andExpect(status().isOk());

        // Re-apply for exact same dates -> succeeds because rejected leaves are excluded from overlap checks
        LeaveRequestDTO reapplyDto = new LeaveRequestDTO();
        reapplyDto.setEmployeeId(empId);
        reapplyDto.setLeaveTypeId(typeId);
        reapplyDto.setStartDate(range[0]);
        reapplyDto.setEndDate(range[1]);
        reapplyDto.setReason("Reapplying with revised justification");

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reapplyDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // =========================================================================
    // Leave Cancellation – additional integration tests
    // =========================================================================

    @Test
    void cancelLeaveRequest_PendingLeave_BalanceNotDeducted() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(205, 3);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Pending cancel test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Balance check before cancel (still 10, untouched since PENDING)
        mockMvc.perform(get("/api/leave-balances/employee/" + empId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(10))
                .andExpect(jsonPath("$[0].used").value(0));

        // Cancel the pending leave request
        mockMvc.perform(patch("/api/leave-requests/" + reqId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Balance should remain untouched after cancelling a pending leave
        mockMvc.perform(get("/api/leave-balances/employee/" + empId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(10))
                .andExpect(jsonPath("$[0].used").value(0));
    }

    @Test
    void cancelLeaveRequest_RejectedLeave_Returns400BadRequest() throws Exception {
        Long empId = createEmployee();
        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(212, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Will be rejected then cancel attempted");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Reject the leave
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/reject")
                .param("reason", "Not enough staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Attempt to cancel rejected leave -> 400 Bad Request
        mockMvc.perform(put("/api/leave-requests/" + reqId + "/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Cannot cancel a rejected")));
    }

    @Test
    void cancelLeaveRequest_SelfService_EmployeeCancelsOwnPendingLeave_Success() throws Exception {
        Long deptId = createDepartment();
        String empEmail = "selfcancel." + System.currentTimeMillis() + "@example.com";
        Long empId = createEmployeeWithEmail(empEmail, deptId);

        Long typeId = createLeaveType();
        LocalDate[] range = getSafeWeekdayRange(220, 2);
        createLeaveBalanceWithDays(empId, typeId, range[0].getYear(), 10);

        LeaveRequestDTO reqDto = new LeaveRequestDTO();
        reqDto.setEmployeeId(empId);
        reqDto.setLeaveTypeId(typeId);
        reqDto.setStartDate(range[0]);
        reqDto.setEndDate(range[1]);
        reqDto.setReason("Self-service cancel test");

        MvcResult result = mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqDto)))
                .andExpect(status().isCreated())
                .andReturn();
        Long reqId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);

        // Employee cancels their own leave using PATCH
        mockMvc.perform(patch("/api/leave-requests/" + reqId + "/cancel")
                .with(user(empEmail).authorities(
                        new SimpleGrantedAuthority("ROLE_EMPLOYEE"),
                        new SimpleGrantedAuthority("LEAVE_CANCEL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
