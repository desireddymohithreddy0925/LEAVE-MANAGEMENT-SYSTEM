package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.PasswordChangeRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin@validation.test", authorities = {
        "ROLE_ADMIN", "DEPARTMENT_MANAGE", "DEPARTMENT_VIEW",
        "EMPLOYEE_CREATE", "EMPLOYEE_VIEW", "EMPLOYEE_UPDATE", "EMPLOYEE_DEACTIVATE",
        "LEAVE_BALANCE_MANAGE", "LEAVE_BALANCE_VIEW", "LEAVE_CREATE", "LEAVE_VIEW_ALL",
        "LEAVE_TYPE_MANAGE", "ROLE_PERMISSION_MANAGE", "LEAVE_APPROVE", "LEAVE_REJECT", "LEAVE_CANCEL"
})
public class ValidationIntegrationTest {

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

    // =========================================================================
    // 1. LeaveRequestDTO Validation Tests
    // =========================================================================

    @Test
    void leaveRequest_MissingRequiredFields_Returns400() throws Exception {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        // leaveTypeId, startDate, endDate are null

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.leaveTypeId").exists())
                .andExpect(jsonPath("$.errors.startDate").exists())
                .andExpect(jsonPath("$.errors.endDate").exists());
    }

    @Test
    void leaveRequest_PastStartDate_Returns400() throws Exception {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setLeaveTypeId(1L);
        dto.setStartDate(LocalDate.now().minusDays(1));
        dto.setEndDate(LocalDate.now().plusDays(2));

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").value(containsString("past")));
    }

    @Test
    void leaveRequest_EndDateBeforeStartDate_Returns400() throws Exception {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setLeaveTypeId(1L);
        dto.setStartDate(LocalDate.now().plusDays(5));
        dto.setEndDate(LocalDate.now().plusDays(2)); // before start date

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endDate").value(containsString("before start date")));
    }

    @Test
    void leaveRequest_CrossCalendarYearDates_CustomValidatorCatches_Returns400() throws Exception {
        int currentYear = LocalDate.now().getYear();
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setLeaveTypeId(1L);
        dto.setStartDate(LocalDate.of(currentYear + 1, 12, 28));
        dto.setEndDate(LocalDate.of(currentYear + 2, 1, 5));

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endDate").value(containsString("multiple calendar years")));
    }

    @Test
    void leaveRequest_ReasonTooLong_Returns400() throws Exception {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setLeaveTypeId(1L);
        dto.setStartDate(LocalDate.now().plusDays(2));
        dto.setEndDate(LocalDate.now().plusDays(4));
        dto.setReason("A".repeat(501));

        mockMvc.perform(post("/api/leave-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
    }

    // =========================================================================
    // 2. LeaveBalanceRequestDTO Validation Tests
    // =========================================================================

    @Test
    void leaveBalance_NegativeDays_Returns400() throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveTypeId(1L);
        dto.setAvailable(-5);
        dto.setTotal(-10);

        mockMvc.perform(post("/api/leave-balances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.available").value(containsString("negative")))
                .andExpect(jsonPath("$.errors.total").value(containsString("negative")));
    }

    @Test
    void leaveBalance_Exceeds365Days_Returns400() throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveTypeId(1L);
        dto.setAvailable(400);
        dto.setTotal(500);

        mockMvc.perform(post("/api/leave-balances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.available").value(containsString("365")));
    }

    @Test
    void leaveBalance_AvailableExceedsTotal_CustomValidatorCatches_Returns400() throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveTypeId(1L);
        dto.setTotal(15);
        dto.setAvailable(25); // exceeds total

        mockMvc.perform(post("/api/leave-balances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.available").value(containsString("Available days cannot exceed total days")));
    }

    @Test
    void leaveBalance_InvalidYear_Returns400() throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveTypeId(1L);
        dto.setYear(1850);

        mockMvc.perform(post("/api/leave-balances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.year").exists());
    }

    // =========================================================================
    // 3. DepartmentRequestDTO Validation Tests
    // =========================================================================

    @Test
    void department_BlankName_Returns400() throws Exception {
        DepartmentRequestDTO dto = new DepartmentRequestDTO();
        dto.setName("   ");

        mockMvc.perform(post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void department_NameExceeds100Chars_Returns400() throws Exception {
        DepartmentRequestDTO dto = new DepartmentRequestDTO();
        dto.setName("D".repeat(101));

        mockMvc.perform(post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // =========================================================================
    // 4. LeaveTypeRequestDTO Validation Tests
    // =========================================================================

    @Test
    void leaveType_MissingRequiredFields_Returns400() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();

        mockMvc.perform(post("/api/leave-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.defaultDays").exists());
    }

    @Test
    void leaveType_ZeroDefaultDays_Returns400() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();
        dto.setName("Vacation");
        dto.setDefaultDays(0);

        mockMvc.perform(post("/api/leave-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.defaultDays").exists());
    }

    @Test
    void leaveType_DefaultDaysExceeds365_Returns400() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();
        dto.setName("Extended Leave");
        dto.setDefaultDays(400);

        mockMvc.perform(post("/api/leave-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.defaultDays").value(containsString("365")));
    }

    // =========================================================================
    // 5. EmployeeRequestDTO Validation Tests
    // =========================================================================

    @Test
    void employee_InvalidEmailFormat_Returns400() throws Exception {
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setEmail("not-an-email");
        dto.setEmployeeCode("EMP-999");
        dto.setStatus("ACTIVE");
        dto.setSalary(new BigDecimal("50000"));
        dto.setDepartmentId(1L);

        mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value(containsString("email")));
    }

    @Test
    void employee_InvalidStatus_CustomValidatorCatches_Returns400() throws Exception {
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setEmail("john.doe@example.com");
        dto.setEmployeeCode("EMP-999");
        dto.setStatus("SUSPENDED"); // Not ACTIVE or INACTIVE
        dto.setSalary(new BigDecimal("50000"));
        dto.setDepartmentId(1L);

        mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value(containsString("Status must be either ACTIVE or INACTIVE")));
    }

    @Test
    void employee_NegativeSalary_Returns400() throws Exception {
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setEmail("john.doe@example.com");
        dto.setEmployeeCode("EMP-999");
        dto.setStatus("ACTIVE");
        dto.setSalary(new BigDecimal("-1000"));
        dto.setDepartmentId(1L);

        mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.salary").value(containsString("positive")));
    }

    // =========================================================================
    // 6. PasswordChangeRequestDTO Validation Tests
    // =========================================================================

    @Test
    void passwordChange_ShortPassword_Returns400() throws Exception {
        PasswordChangeRequestDTO dto = new PasswordChangeRequestDTO("oldPass123", "short");

        mockMvc.perform(put("/api/employees/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value(containsString("8")));
    }

    // =========================================================================
    // 7. RejectRequestDTO Validation Tests
    // =========================================================================

    @Test
    void rejectRequest_BlankReason_Returns400() throws Exception {
        com.leave_management_system.leave_management_system.dto.RejectRequestDTO dto = new com.leave_management_system.leave_management_system.dto.RejectRequestDTO();
        dto.setReason("   ");

        mockMvc.perform(post("/api/leave-requests/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value(containsString("mandatory")));
    }

    @Test
    void rejectRequest_OverlongReason_Returns400() throws Exception {
        com.leave_management_system.leave_management_system.dto.RejectRequestDTO dto = new com.leave_management_system.leave_management_system.dto.RejectRequestDTO();
        dto.setReason("R".repeat(501));

        mockMvc.perform(post("/api/leave-requests/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value(containsString("500")));
    }
}
