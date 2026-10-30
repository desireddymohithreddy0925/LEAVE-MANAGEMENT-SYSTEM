package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveBalanceResponseDTO;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.security.SecurityAuthorizationService;
import com.leave_management_system.leave_management_system.service.LeaveBalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LeaveBalanceControllerTest {

    @Mock
    private LeaveBalanceService leaveBalanceService;

    @Mock
    private SecurityAuthorizationService securityService;

    @InjectMocks
    private LeaveBalanceController leaveBalanceController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(leaveBalanceController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .build();
    }

    @Test
    @DisplayName("GET /api/leave-balances/me - Success returns user balances")
    void getMyBalances_Success() throws Exception {
        Employee employee = new Employee();
        employee.setId(10L);

        LeaveBalanceResponseDTO dto = new LeaveBalanceResponseDTO();
        dto.setId(1L);
        dto.setAvailable(15);
        dto.setTotal(20);

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("user@example.com", null, Collections.emptyList());

        when(securityService.getAuthenticatedEmployee(any(Authentication.class))).thenReturn(Optional.of(employee));
        when(leaveBalanceService.getBalancesByEmployee(10L, null)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/leave-balances/me").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].available").value(15));

        verify(leaveBalanceService).getBalancesByEmployee(10L, null);
    }

    @Test
    @DisplayName("GET /api/leave-balances/me - Employee not found returns 404")
    void getMyBalances_NotFound() throws Exception {
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("user@example.com", null, Collections.emptyList());

        when(securityService.getAuthenticatedEmployee(any(Authentication.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/leave-balances/me").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/leave-balances - Success returns 201 Created")
    void createLeaveBalance_Success() throws Exception {
        LeaveBalanceRequestDTO request = new LeaveBalanceRequestDTO(1L, 2L, 20, 2026, 20);
        LeaveBalanceResponseDTO response = new LeaveBalanceResponseDTO();
        response.setId(5L);
        response.setAvailable(20);
        response.setTotal(20);

        when(leaveBalanceService.createLeaveBalance(any(LeaveBalanceRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/leave-balances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.available").value(20));

        verify(leaveBalanceService).createLeaveBalance(any(LeaveBalanceRequestDTO.class));
    }

    @Test
    @DisplayName("GET /api/leave-balances - Returns all balances")
    void getAllLeaveBalances_Success() throws Exception {
        LeaveBalanceResponseDTO dto = new LeaveBalanceResponseDTO();
        dto.setId(1L);

        when(leaveBalanceService.getAllLeaveBalances()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/leave-balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));

        verify(leaveBalanceService).getAllLeaveBalances();
    }

    @Test
    @DisplayName("GET /api/leave-balances/{id} - Success returns balance")
    void getLeaveBalanceById_Success() throws Exception {
        LeaveBalanceResponseDTO dto = new LeaveBalanceResponseDTO();
        dto.setId(1L);
        dto.setAvailable(10);

        when(leaveBalanceService.getLeaveBalanceById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/leave-balances/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.available").value(10));

        verify(leaveBalanceService).getLeaveBalanceById(1L);
    }

    @Test
    @DisplayName("GET /api/leave-balances/{id} - Not found returns 404")
    void getLeaveBalanceById_NotFound() throws Exception {
        when(leaveBalanceService.getLeaveBalanceById(99L)).thenThrow(new ResourceNotFoundException("Leave balance not found"));

        mockMvc.perform(get("/api/leave-balances/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(leaveBalanceService).getLeaveBalanceById(99L);
    }

    @Test
    @DisplayName("GET /api/leave-balances/employee/{id} - Returns employee balances")
    void getBalancesByEmployee_Success() throws Exception {
        LeaveBalanceResponseDTO dto = new LeaveBalanceResponseDTO();
        dto.setId(1L);

        when(leaveBalanceService.getBalancesByEmployee(10L, 2026)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/leave-balances/employee/10").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(leaveBalanceService).getBalancesByEmployee(10L, 2026);
    }

    @Test
    @DisplayName("POST /api/leave-balances/employee/{id}/initialize - Initializes employee balances")
    void initializeBalances_Success() throws Exception {
        LeaveBalanceResponseDTO dto = new LeaveBalanceResponseDTO();
        dto.setId(1L);

        when(leaveBalanceService.initializeBalancesForEmployee(10L, 2026)).thenReturn(List.of(dto));

        mockMvc.perform(post("/api/leave-balances/employee/10/initialize").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(leaveBalanceService).initializeBalancesForEmployee(10L, 2026);
    }

    @Test
    @DisplayName("PUT /api/leave-balances/{id} - Updates balance")
    void updateLeaveBalance_Success() throws Exception {
        LeaveBalanceRequestDTO request = new LeaveBalanceRequestDTO(1L, 2L, 18, 2026, 20);
        LeaveBalanceResponseDTO response = new LeaveBalanceResponseDTO();
        response.setId(1L);
        response.setAvailable(18);

        when(leaveBalanceService.updateLeaveBalance(eq(1L), any(LeaveBalanceRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/leave-balances/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(18));

        verify(leaveBalanceService).updateLeaveBalance(eq(1L), any(LeaveBalanceRequestDTO.class));
    }

    @Test
    @DisplayName("DELETE /api/leave-balances/{id} - Deletes balance returns 204")
    void deleteLeaveBalance_Success() throws Exception {
        doNothing().when(leaveBalanceService).deleteLeaveBalance(1L);

        mockMvc.perform(delete("/api/leave-balances/1"))
                .andExpect(status().isNoContent());

        verify(leaveBalanceService).deleteLeaveBalance(1L);
    }
}

