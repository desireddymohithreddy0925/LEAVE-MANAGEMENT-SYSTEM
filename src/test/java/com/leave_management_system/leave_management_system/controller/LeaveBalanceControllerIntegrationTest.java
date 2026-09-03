package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LeaveBalanceControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    private Long createDepartment() throws Exception {
        com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO dto = new com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO();
        dto.setName("Bal Dept " + System.currentTimeMillis());
        MvcResult result = mockMvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Long createEmployee() throws Exception {
        Long deptId = createDepartment();
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("Balance");
        dto.setLastName("Test");
        dto.setEmail("balance.test@example.com");
        dto.setPhone("1234567890");
        dto.setDepartmentId(deptId);

        MvcResult result = mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Long createLeaveType() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();
        dto.setName("Integration Balance Leave");
        dto.setDescription("Test");
        dto.setDefaultDays(12);

        MvcResult result = mockMvc.perform(post("/api/leave-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    @Test
    void createLeaveBalance_Success() throws Exception {
        Long employeeId = createEmployee();
        Long leaveTypeId = createLeaveType();

        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(employeeId);
        dto.setLeaveTypeId(leaveTypeId);
        dto.setAvailableDays(20);

        mockMvc.perform(post("/api/leave-balances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableDays").value(20));
    }

    @Test
    void createLeaveBalance_Duplicate() throws Exception {
        Long employeeId = createEmployee();
        Long leaveTypeId = createLeaveType();

        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(employeeId);
        dto.setLeaveTypeId(leaveTypeId);
        dto.setAvailableDays(20);

        mockMvc.perform(post("/api/leave-balances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Second insert same year/type/employee should fail
        mockMvc.perform(post("/api/leave-balances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }
}
