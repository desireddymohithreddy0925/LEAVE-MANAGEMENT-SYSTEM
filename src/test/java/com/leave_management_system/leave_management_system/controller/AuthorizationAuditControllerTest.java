package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.AuthorizationAuditResponseDTO;
import com.leave_management_system.leave_management_system.dto.AuthorizationSummaryDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
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
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationAuditControllerTest {

    @Mock
    private AuthorizationAuditService authorizationAuditService;

    @InjectMocks
    private AuthorizationAuditController authorizationAuditController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(authorizationAuditController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/authorization-audits - Search authorization audits returns page")
    void searchAuthorizationAudits_Success() throws Exception {
        AuthorizationAuditResponseDTO dto = new AuthorizationAuditResponseDTO();
        dto.setId(1L);
        dto.setAction("EMPLOYEE_CREATE");
        dto.setDecision("GRANTED");
        dto.setTimestamp(LocalDateTime.now());

        Page<AuthorizationAuditResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(authorizationAuditService.searchAuthorizationAudits(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/authorization-audits")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].decision").value("GRANTED"));

        verify(authorizationAuditService).searchAuthorizationAudits(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/authorization-audits/summary - Returns summary metrics")
    void getSummaryMetrics_Success() throws Exception {
        AuthorizationSummaryDTO summary = new AuthorizationSummaryDTO(100L, 95L, 5L, 5.0, LocalDateTime.now());

        when(authorizationAuditService.getSummaryMetrics()).thenReturn(summary);

        mockMvc.perform(get("/api/authorization-audits/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDecisions").value(100))
                .andExpect(jsonPath("$.grantedCount").value(95))
                .andExpect(jsonPath("$.deniedCount").value(5))
                .andExpect(jsonPath("$.denialRatePercentage").value(5.0));

        verify(authorizationAuditService).getSummaryMetrics();
    }

    @Test
    @DisplayName("GET /api/authorization-audits/me - Returns current user authorization audits")
    void getMyAuthorizationAudits_Success() throws Exception {
        UserDetails userDetails = new User("user@example.com", "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(principal);

        AuthorizationAuditResponseDTO dto = new AuthorizationAuditResponseDTO();
        dto.setId(1L);
        dto.setDecision("GRANTED");
        dto.setTimestamp(LocalDateTime.now());

        Page<AuthorizationAuditResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(authorizationAuditService.getAuditsForCurrentUser(any(UserDetails.class), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/authorization-audits/me")
                        .principal(principal)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));

        verify(authorizationAuditService).getAuditsForCurrentUser(any(UserDetails.class), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/authorization-audits/user/{userId} - Returns user authorization audits")
    void getAuthorizationAuditsForUser_Success() throws Exception {
        AuthorizationAuditResponseDTO dto = new AuthorizationAuditResponseDTO();
        dto.setId(1L);
        dto.setUserId(5L);
        dto.setTimestamp(LocalDateTime.now());

        Page<AuthorizationAuditResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(authorizationAuditService.getAuditsForUser(eq(5L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/authorization-audits/user/5")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(5));

        verify(authorizationAuditService).getAuditsForUser(eq(5L), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/authorization-audits/{id} - Success returns authorization audit")
    void getAuthorizationAuditById_Success() throws Exception {
        AuthorizationAuditResponseDTO dto = new AuthorizationAuditResponseDTO();
        dto.setId(1L);
        dto.setTimestamp(LocalDateTime.now());

        when(authorizationAuditService.getAuditById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/authorization-audits/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(authorizationAuditService).getAuditById(1L);
    }

    @Test
    @DisplayName("GET /api/authorization-audits/{id} - Not found returns 404")
    void getAuthorizationAuditById_NotFound() throws Exception {
        when(authorizationAuditService.getAuditById(99L)).thenThrow(new ResourceNotFoundException("Authorization audit log not found with id: 99"));

        mockMvc.perform(get("/api/authorization-audits/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(authorizationAuditService).getAuditById(99L);
    }
}
