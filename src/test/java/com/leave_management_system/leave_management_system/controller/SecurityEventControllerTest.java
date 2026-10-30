package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.SecurityEventResponseDTO;
import com.leave_management_system.leave_management_system.dto.SecurityEventSummaryDTO;
import com.leave_management_system.leave_management_system.dto.UpdateSecurityEventStatusDTO;
import com.leave_management_system.leave_management_system.entity.SecurityEventSeverity;
import com.leave_management_system.leave_management_system.entity.SecurityEventStatus;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.service.SecurityEventManagementService;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SecurityEventControllerTest {

    @Mock
    private SecurityEventManagementService securityEventManagementService;

    @InjectMocks
    private SecurityEventController securityEventController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(securityEventController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/security-events - Search security events returns page")
    void searchSecurityEvents_Success() throws Exception {
        SecurityEventResponseDTO dto = new SecurityEventResponseDTO();
        dto.setId(1L);
        dto.setEventType("BRUTE_FORCE_ATTEMPT");
        dto.setSeverity(SecurityEventSeverity.HIGH);
        dto.setStatus(SecurityEventStatus.OPEN);
        dto.setCreatedAt(LocalDateTime.now());

        Page<SecurityEventResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(securityEventManagementService.searchSecurityEvents(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/security-events")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("BRUTE_FORCE_ATTEMPT"));

        verify(securityEventManagementService).searchSecurityEvents(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/security-events/summary - Returns security event summary")
    void getSummary_Success() throws Exception {
        SecurityEventSummaryDTO summary = new SecurityEventSummaryDTO();
        summary.setTotalEvents(10L);
        summary.setOpenCount(2L);
        summary.setInvestigatingCount(3L);
        summary.setResolvedCount(5L);

        when(securityEventManagementService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/security-events/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(10))
                .andExpect(jsonPath("$.openCount").value(2))
                .andExpect(jsonPath("$.resolvedCount").value(5));

        verify(securityEventManagementService).getSummary();
    }

    @Test
    @DisplayName("GET /api/security-events/{id} - Success returns security event")
    void getSecurityEventById_Success() throws Exception {
        SecurityEventResponseDTO dto = new SecurityEventResponseDTO();
        dto.setId(1L);
        dto.setEventType("SUSPICIOUS_LOCATION");
        dto.setCreatedAt(LocalDateTime.now());

        when(securityEventManagementService.getSecurityEventById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/security-events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.eventType").value("SUSPICIOUS_LOCATION"));

        verify(securityEventManagementService).getSecurityEventById(1L);
    }

    @Test
    @DisplayName("GET /api/security-events/{id} - Not found returns 404")
    void getSecurityEventById_NotFound() throws Exception {
        when(securityEventManagementService.getSecurityEventById(99L))
                .thenThrow(new ResourceNotFoundException("Security event not found with id: 99"));

        mockMvc.perform(get("/api/security-events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(securityEventManagementService).getSecurityEventById(99L);
    }

    @Test
    @DisplayName("PATCH /api/security-events/{id}/status - Updates security event status")
    void updateStatus_Success() throws Exception {
        UpdateSecurityEventStatusDTO request = new UpdateSecurityEventStatusDTO(SecurityEventStatus.RESOLVED, "Issue resolved");
        SecurityEventResponseDTO response = new SecurityEventResponseDTO();
        response.setId(1L);
        response.setStatus(SecurityEventStatus.RESOLVED);
        response.setResolutionNotes("Issue resolved");
        response.setCreatedAt(LocalDateTime.now());

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, Collections.emptyList());

        when(securityEventManagementService.updateStatus(eq(1L), any(UpdateSecurityEventStatusDTO.class), any(Authentication.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/security-events/1/status")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNotes").value("Issue resolved"));

        verify(securityEventManagementService).updateStatus(eq(1L), any(UpdateSecurityEventStatusDTO.class), any(Authentication.class));
    }

    @Test
    @DisplayName("POST /api/security-events/{id}/investigate - Marks incident under investigation")
    void investigate_Success() throws Exception {
        SecurityEventResponseDTO response = new SecurityEventResponseDTO();
        response.setId(1L);
        response.setStatus(SecurityEventStatus.INVESTIGATING);
        response.setCreatedAt(LocalDateTime.now());

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, Collections.emptyList());

        when(securityEventManagementService.updateStatus(eq(1L), any(UpdateSecurityEventStatusDTO.class), any(Authentication.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/security-events/1/investigate")
                        .principal(principal)
                        .param("notes", "Investigating source IP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));

        verify(securityEventManagementService).updateStatus(eq(1L), any(UpdateSecurityEventStatusDTO.class), any(Authentication.class));
    }

    @Test
    @DisplayName("POST /api/security-events/{id}/resolve - Resolves security event")
    void resolve_Success() throws Exception {
        SecurityEventResponseDTO response = new SecurityEventResponseDTO();
        response.setId(1L);
        response.setStatus(SecurityEventStatus.RESOLVED);
        response.setCreatedAt(LocalDateTime.now());

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, Collections.emptyList());

        when(securityEventManagementService.updateStatus(eq(1L), any(UpdateSecurityEventStatusDTO.class), any(Authentication.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/security-events/1/resolve")
                        .principal(principal)
                        .param("notes", "IP address blocked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        verify(securityEventManagementService).updateStatus(eq(1L), any(UpdateSecurityEventStatusDTO.class), any(Authentication.class));
    }

    @Test
    @DisplayName("POST /api/security-events/{id}/false-positive - Marks incident as false positive")
    void falsePositive_Success() throws Exception {
        SecurityEventResponseDTO response = new SecurityEventResponseDTO();
        response.setId(1L);
        response.setStatus(SecurityEventStatus.FALSE_POSITIVE);
        response.setCreatedAt(LocalDateTime.now());

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, Collections.emptyList());

        when(securityEventManagementService.updateStatus(eq(1L), any(UpdateSecurityEventStatusDTO.class), any(Authentication.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/security-events/1/false-positive")
                        .principal(principal)
                        .param("notes", "Benign internal scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALSE_POSITIVE"));

        verify(securityEventManagementService).updateStatus(eq(1L), any(UpdateSecurityEventStatusDTO.class), any(Authentication.class));
    }
}

