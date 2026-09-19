package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class EmployeeControllerIntegrationTest {

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
        dto.setName("Test Dept " + System.currentTimeMillis());
        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andReturn();
        return com.jayway.jsonpath.JsonPath.parse(result.getResponse().getContentAsString()).read("$.id", Long.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createEmployee_Success() throws Exception {
        Long deptId = createDepartment();
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("Integration");
        dto.setLastName("Test");
        dto.setEmail("integration.test@example.com");
        dto.setPhone("1234567890");
        dto.setDepartmentId(deptId);
        dto.setEmployeeCode("EMP-" + System.currentTimeMillis());
        dto.setStatus("ACTIVE");
        dto.setSalary(new java.math.BigDecimal("50000"));

        mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("integration.test@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createEmployee_ValidationFails() throws Exception {
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        // Missing required fields

        mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchEmployees_Success() throws Exception {
        mockMvc.perform(get("/api/employees").param("search", "admin"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createEmployee_DuplicateEmail() throws Exception {
        Long deptId = createDepartment();
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("First");
        dto.setLastName("Last");
        dto.setEmail("duplicate.test@example.com");
        dto.setPhone("1234567890");
        dto.setDepartmentId(deptId);
        dto.setEmployeeCode("EMP-" + System.currentTimeMillis());
        dto.setStatus("ACTIVE");
        dto.setSalary(new java.math.BigDecimal("50000"));

        mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void createEmployee_UnauthorizedForEmployee() throws Exception {
        EmployeeRequestDTO dto = new EmployeeRequestDTO();
        dto.setFirstName("First");
        dto.setLastName("Last");
        dto.setEmail("unauthorized@example.com");
        dto.setPhone("1234567890");
        dto.setDepartmentId(1L);
        dto.setEmployeeCode("EMP-" + System.currentTimeMillis());
        dto.setStatus("ACTIVE");
        dto.setSalary(new java.math.BigDecimal("50000"));

        mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden()); // 403 Forbidden
    }
}
