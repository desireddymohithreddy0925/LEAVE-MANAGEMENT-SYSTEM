package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.dto.TeamHierarchyDTO;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.security.SecurityAuthorizationService;
import com.leave_management_system.leave_management_system.service.EmployeeService;
import com.leave_management_system.leave_management_system.service.LeaveRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ManagerControllerTest {

    @Mock
    private EmployeeService employeeService;

    @Mock
    private LeaveRequestService leaveRequestService;

    @Mock
    private SecurityAuthorizationService securityService;

    @InjectMocks
    private ManagerController managerController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(managerController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .build();
    }

    @Test
    @DisplayName("GET /api/managers/me/team - Returns authenticated manager team")
    void getMyTeam_Success() throws Exception {
        Employee manager = new Employee();
        manager.setId(5L);

        EmployeeResponseDTO e1 = new EmployeeResponseDTO();
        e1.setId(10L);
        e1.setFirstName("Alice");

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "manager@example.com", null, Collections.emptyList());

        when(securityService.getAuthenticatedEmployee(any(Authentication.class))).thenReturn(Optional.of(manager));
        when(employeeService.getManagerTeam(5L)).thenReturn(List.of(e1));

        mockMvc.perform(get("/api/managers/me/team").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].firstName").value("Alice"));

        verify(employeeService).getManagerTeam(5L);
    }

    @Test
    @DisplayName("GET /api/managers/me/team-hierarchy - Returns team hierarchy")
    void getMyTeamHierarchy_Success() throws Exception {
        Employee manager = new Employee();
        manager.setId(5L);

        TeamHierarchyDTO hierarchy = new TeamHierarchyDTO();
        hierarchy.setManagerId(5L);
        hierarchy.setManagerName("Bob Jones");

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "manager@example.com", null, Collections.emptyList());

        when(securityService.getAuthenticatedEmployee(any(Authentication.class))).thenReturn(Optional.of(manager));
        when(employeeService.getTeamHierarchy(5L)).thenReturn(hierarchy);

        mockMvc.perform(get("/api/managers/me/team-hierarchy").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(5))
                .andExpect(jsonPath("$.managerName").value("Bob Jones"));

        verify(employeeService).getTeamHierarchy(5L);
    }

    @Test
    @DisplayName("GET /api/managers/{id}/team - Returns team by manager ID")
    void getManagerTeamById_Success() throws Exception {
        EmployeeResponseDTO e1 = new EmployeeResponseDTO();
        e1.setId(10L);

        when(employeeService.getManagerTeam(5L)).thenReturn(List.of(e1));

        mockMvc.perform(get("/api/managers/5/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10));

        verify(employeeService).getManagerTeam(5L);
    }

    @Test
    @DisplayName("GET /api/managers/{id}/team-hierarchy - Returns team hierarchy by manager ID")
    void getManagerHierarchyById_Success() throws Exception {
        TeamHierarchyDTO hierarchy = new TeamHierarchyDTO();
        hierarchy.setManagerId(5L);

        when(employeeService.getTeamHierarchy(5L)).thenReturn(hierarchy);

        mockMvc.perform(get("/api/managers/5/team-hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId").value(5));

        verify(employeeService).getTeamHierarchy(5L);
    }

    @Test
    @DisplayName("GET /api/managers/me/leave-requests - Returns team leave requests")
    void getMyTeamLeaveRequests_Success() throws Exception {
        Employee manager = new Employee();
        manager.setId(5L);

        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(100L);

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "manager@example.com", null, Collections.emptyList());

        when(securityService.getAuthenticatedEmployee(any(Authentication.class))).thenReturn(Optional.of(manager));
        when(leaveRequestService.getTeamLeaveRequests(5L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/managers/me/leave-requests").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(100));

        verify(leaveRequestService).getTeamLeaveRequests(5L);
    }
}

