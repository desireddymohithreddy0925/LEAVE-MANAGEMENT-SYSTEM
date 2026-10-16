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
        if (start.getYear() > LocalDate.now().getYear()) {
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
        createLeaveBalanceWithDays(emp1Id, typeId, range1[0].getYear(), 10);
        createLeaveBalanceWithDays(emp2Id, typeId, range1[0].getYear(), 10);

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
        LocalDate[] range2 = getSafeWeekdayRange(65, 2);
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
}
