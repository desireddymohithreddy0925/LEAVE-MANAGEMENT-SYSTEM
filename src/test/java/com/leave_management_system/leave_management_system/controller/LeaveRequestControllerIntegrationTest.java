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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LeaveRequestControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    private Long createDepartment() throws Exception {
        com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO dto = new com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO();
        dto.setName("Req Dept " + System.currentTimeMillis());
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
        MvcResult result = mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Long createLeaveType() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();
        dto.setName("Req Leave");
        dto.setDescription("Test");
        dto.setDefaultDays(12);
        MvcResult result = mockMvc.perform(post("/api/leave-types").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andReturn();
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private void createLeaveBalance(Long empId, Long typeId) throws Exception {
        LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
        dto.setEmployeeId(empId);
        dto.setLeaveTypeId(typeId);
        dto.setAvailableDays(20);
        mockMvc.perform(post("/api/leave-balances").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)));
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

        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(empId);
        dto.setLeaveTypeId(typeId);
        dto.setStartDate(LocalDate.now().plusDays(10));
        dto.setEndDate(LocalDate.now().plusDays(12));
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
}
