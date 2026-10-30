package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LoginHistoryResponseDTO;
import com.leave_management_system.leave_management_system.dto.LoginSummaryDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.service.LoginTrackingService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LoginHistoryControllerTest {

    @Mock
    private LoginTrackingService loginTrackingService;

    @InjectMocks
    private LoginHistoryController loginHistoryController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(loginHistoryController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/login-history/me - Returns current user login history")
    void getMyLoginHistory_Success() throws Exception {
        LoginHistoryResponseDTO dto = new LoginHistoryResponseDTO();
        dto.setId(1L);
        dto.setUserEmail("user@example.com");
        dto.setLoginTime(LocalDateTime.now());

        Page<LoginHistoryResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(loginTrackingService.getLoginHistoryForCurrentUser(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/login-history/me")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].userEmail").value("user@example.com"));

        verify(loginTrackingService).getLoginHistoryForCurrentUser(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/login-history/me/summary - Returns current user login summary")
    void getMyLoginSummary_Success() throws Exception {
        LoginSummaryDTO summary = new LoginSummaryDTO();
        summary.setUserId(1L);
        summary.setEmail("user@example.com");
        summary.setTotalLogins(15L);
        summary.setFailedAttempts(2L);

        when(loginTrackingService.getCurrentUserSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/login-history/me/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.totalLogins").value(15));

        verify(loginTrackingService).getCurrentUserSummary();
    }

    @Test
    @DisplayName("GET /api/login-history - Administrative search of login history")
    void searchLoginHistory_Success() throws Exception {
        LoginHistoryResponseDTO dto = new LoginHistoryResponseDTO();
        dto.setId(1L);
        dto.setUserEmail("user@example.com");
        dto.setStatus("SUCCESS");
        dto.setLoginTime(LocalDateTime.now());

        Page<LoginHistoryResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(loginTrackingService.searchLoginHistory(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/login-history")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));

        verify(loginTrackingService).searchLoginHistory(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/login-history/user/{userId} - Returns login history for specific user")
    void getUserLoginHistory_Success() throws Exception {
        LoginHistoryResponseDTO dto = new LoginHistoryResponseDTO();
        dto.setId(1L);
        dto.setUserId(5L);
        dto.setLoginTime(LocalDateTime.now());

        Page<LoginHistoryResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(loginTrackingService.getLoginHistoryForUser(eq(5L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/login-history/user/5")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(5));

        verify(loginTrackingService).getLoginHistoryForUser(eq(5L), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/login-history/user/{userId}/summary - Returns login summary for specific user")
    void getUserLoginSummary_Success() throws Exception {
        LoginSummaryDTO summary = new LoginSummaryDTO();
        summary.setUserId(5L);
        summary.setEmail("employee@example.com");
        summary.setTotalLogins(30L);

        when(loginTrackingService.getUserSummary(5L)).thenReturn(summary);

        mockMvc.perform(get("/api/login-history/user/5/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.totalLogins").value(30));

        verify(loginTrackingService).getUserSummary(5L);
    }

    @Test
    @DisplayName("GET /api/login-history/user/{userId}/summary - User not found returns 404")
    void getUserLoginSummary_NotFound() throws Exception {
        when(loginTrackingService.getUserSummary(99L)).thenThrow(new ResourceNotFoundException("User not found with id: 99"));

        mockMvc.perform(get("/api/login-history/user/99/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verify(loginTrackingService).getUserSummary(99L);
    }
}

