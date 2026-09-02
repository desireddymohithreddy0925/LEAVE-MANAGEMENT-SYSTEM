package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LeaveTypeControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    @Test
    void createLeaveType_Success() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();
        dto.setName("Integration Sick Leave");
        dto.setDescription("Test");
        dto.setDefaultDays(12);

        mockMvc.perform(post("/api/leave-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Integration Sick Leave"));
    }

    @Test
    void createLeaveType_ValidationFails() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();
        // Missing name, will fail validation

        mockMvc.perform(post("/api/leave-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateLeaveType_Success() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();
        dto.setName("Integration Annual");
        dto.setDescription("Test");
        dto.setDefaultDays(14);

        MvcResult result = mockMvc.perform(post("/api/leave-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        
        String responseString = result.getResponse().getContentAsString();
        Integer id = JsonPath.parse(responseString).read("$.id");

        // Default is active, let's deactivate then activate
        mockMvc.perform(put("/api/leave-types/" + id + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(put("/api/leave-types/" + id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }
}
