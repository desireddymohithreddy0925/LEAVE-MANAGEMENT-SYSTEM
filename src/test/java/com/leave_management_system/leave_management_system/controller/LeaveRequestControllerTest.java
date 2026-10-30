package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.dto.LeaveSignoffResponseDTO;
import com.leave_management_system.leave_management_system.dto.OverlapCheckResponseDTO;
import com.leave_management_system.leave_management_system.dto.RejectRequestDTO;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.security.SecurityAuthorizationService;
import com.leave_management_system.leave_management_system.service.LeaveRequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LeaveRequestControllerTest {

    @Mock
    private LeaveRequestService leaveRequestService;

    @Mock
    private SecurityAuthorizationService securityService;

    @InjectMocks
    private LeaveRequestController leaveRequestController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(leaveRequestController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private LeaveRequestDTO createValidLeaveRequestDTO() {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setEmployeeId(1L);
        dto.setLeaveTypeId(2L);
        dto.setStartDate(LocalDate.now().plusDays(2));
        dto.setEndDate(LocalDate.now().plusDays(4));
        dto.setReason("Personal vacation");
        return dto;
    }

    @Test
    @DisplayName("POST /api/leave-requests - Admin creates leave request returns 201 Created")
    void createLeaveRequest_AsAdmin_Success() throws Exception {
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        LeaveRequestDTO request = createValidLeaveRequestDTO();
        LeaveResponseDTO response = new LeaveResponseDTO();
        response.setId(10L);
        response.setStatus(LeaveStatus.PENDING);

        when(leaveRequestService.createLeaveRequest(any(LeaveRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/leave-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(leaveRequestService).createLeaveRequest(any(LeaveRequestDTO.class));
    }

    @Test
    @DisplayName("POST /api/leave-requests - Regular employee creates leave request for self")
    void createLeaveRequest_AsEmployee_Success() throws Exception {
        UsernamePasswordAuthenticationToken empAuth = new UsernamePasswordAuthenticationToken(
                "emp@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
        SecurityContextHolder.getContext().setAuthentication(empAuth);

        Employee emp = new Employee();
        emp.setId(7L);
        when(securityService.getAuthenticatedEmployee(empAuth)).thenReturn(Optional.of(emp));

        LeaveRequestDTO request = createValidLeaveRequestDTO();
        LeaveResponseDTO response = new LeaveResponseDTO();
        response.setId(11L);
        response.setStatus(LeaveStatus.PENDING);

        when(leaveRequestService.createLeaveRequest(any(LeaveRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/leave-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11));

        verify(leaveRequestService).createLeaveRequest(any(LeaveRequestDTO.class));
    }

    @Test
    @DisplayName("GET /api/leave-requests/check-overlap - Checks overlapping dates")
    void checkOverlap_Success() throws Exception {
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(3);

        OverlapCheckResponseDTO response = new OverlapCheckResponseDTO(false, "No overlap detected", Collections.emptyList());

        when(leaveRequestService.checkOverlap(1L, start, end)).thenReturn(response);

        mockMvc.perform(get("/api/leave-requests/check-overlap")
                        .principal(adminAuth)
                        .param("startDate", start.toString())
                        .param("endDate", end.toString())
                        .param("employeeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapping").value(false))
                .andExpect(jsonPath("$.message").value("No overlap detected"));

        verify(leaveRequestService).checkOverlap(1L, start, end);
    }

    @Test
    @DisplayName("GET /api/leave-requests/me - Returns current employee leave requests")
    void getMyLeaveRequests_Success() throws Exception {
        UsernamePasswordAuthenticationToken empAuth = new UsernamePasswordAuthenticationToken(
                "emp@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
        Employee emp = new Employee();
        emp.setId(7L);
        when(securityService.getAuthenticatedEmployee(empAuth)).thenReturn(Optional.of(emp));

        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(10L);
        Page<LeaveResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(leaveRequestService.getMyLeaveRequests(eq(7L), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/leave-requests/me")
                        .principal(empAuth)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10));

        verify(leaveRequestService).getMyLeaveRequests(eq(7L), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/leave-requests - Search leave requests with pagination")
    void getLeaveRequests_Success() throws Exception {
        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(10L);
        Page<LeaveResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(leaveRequestService.searchLeaveRequests(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/leave-requests")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10));

        verify(leaveRequestService).searchLeaveRequests(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/leave-requests/{id} - Success returns leave request")
    void getLeaveRequestById_Success() throws Exception {
        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(10L);

        when(leaveRequestService.getLeaveRequestById(10L)).thenReturn(dto);

        mockMvc.perform(get("/api/leave-requests/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(leaveRequestService).getLeaveRequestById(10L);
    }

    @Test
    @DisplayName("GET /api/leave-requests/{id} - Not found returns 404")
    void getLeaveRequestById_NotFound() throws Exception {
        when(leaveRequestService.getLeaveRequestById(99L)).thenThrow(new ResourceNotFoundException("Leave request not found with id: 99"));

        mockMvc.perform(get("/api/leave-requests/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(leaveRequestService).getLeaveRequestById(99L);
    }

    @Test
    @DisplayName("GET /api/leave-requests/employee/{employeeId} - Returns employee leave requests")
    void getLeaveRequestsByEmployee_Success() throws Exception {
        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(10L);

        when(leaveRequestService.getLeaveRequestsByEmployee(5L)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/leave-requests/employee/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(leaveRequestService).getLeaveRequestsByEmployee(5L);
    }

    @Test
    @DisplayName("GET /api/leave-requests/status/{status} - Returns requests by status")
    void getLeaveRequestsByStatus_Success() throws Exception {
        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(10L);
        dto.setStatus(LeaveStatus.PENDING);

        when(leaveRequestService.getLeaveRequestsByStatus(LeaveStatus.PENDING)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/leave-requests/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(leaveRequestService).getLeaveRequestsByStatus(LeaveStatus.PENDING);
    }

    @Test
    @DisplayName("PUT /api/leave-requests/{id}/approve - Approves leave request")
    void approveLeaveRequest_Success() throws Exception {
        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(10L);
        dto.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestService.approveLeaveRequest(10L)).thenReturn(dto);

        mockMvc.perform(put("/api/leave-requests/10/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(leaveRequestService).approveLeaveRequest(10L);
    }

    @Test
    @DisplayName("PUT /api/leave-requests/{id}/reject - Rejects leave request with reason")
    void rejectLeaveRequest_Success() throws Exception {
        RejectRequestDTO body = new RejectRequestDTO();
        body.setReason("Operational priorities");

        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(10L);
        dto.setStatus(LeaveStatus.REJECTED);
        dto.setRejectionReason("Operational priorities");

        when(leaveRequestService.rejectLeaveRequest(10L, "Operational priorities")).thenReturn(dto);

        mockMvc.perform(put("/api/leave-requests/10/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Operational priorities"));

        verify(leaveRequestService).rejectLeaveRequest(10L, "Operational priorities");
    }

    @Test
    @DisplayName("PUT /api/leave-requests/{id}/cancel - Cancels leave request")
    void cancelLeaveRequest_Success() throws Exception {
        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(10L);
        dto.setStatus(LeaveStatus.CANCELLED);

        when(leaveRequestService.cancelLeaveRequest(10L)).thenReturn(dto);

        mockMvc.perform(put("/api/leave-requests/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(leaveRequestService).cancelLeaveRequest(10L);
    }

    @Test
    @DisplayName("GET /api/leave-requests/{id}/signoffs - Returns signoffs list")
    void getLeaveRequestSignoffs_Success() throws Exception {
        LeaveSignoffResponseDTO signoff = new LeaveSignoffResponseDTO();
        signoff.setId(1L);
        signoff.setStatus("APPROVED");

        when(leaveRequestService.getLeaveRequestSignoffs(10L)).thenReturn(List.of(signoff));

        mockMvc.perform(get("/api/leave-requests/10/signoffs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));

        verify(leaveRequestService).getLeaveRequestSignoffs(10L);
    }
}
