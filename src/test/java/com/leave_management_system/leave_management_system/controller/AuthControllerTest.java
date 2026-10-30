package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.AuthRequestDTO;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.leave_management_system.leave_management_system.dto.AuthResponseDTO;
import com.leave_management_system.leave_management_system.dto.LoginHistoryResponseDTO;
import com.leave_management_system.leave_management_system.dto.PasswordChangeRequestDTO;
import com.leave_management_system.leave_management_system.dto.RegisterRequestDTO;
import com.leave_management_system.leave_management_system.dto.TokenRefreshRequestDTO;
import com.leave_management_system.leave_management_system.dto.UserResponseDTO;
import com.leave_management_system.leave_management_system.exception.GlobalExceptionHandler;
import com.leave_management_system.leave_management_system.service.AuthService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private LoginTrackingService loginTrackingService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setMessageConverters(new StringHttpMessageConverter(), jacksonConverter)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/login - Success returns 200 with tokens")
    void authenticateUser_Success() throws Exception {
        AuthRequestDTO request = new AuthRequestDTO("test@example.com", "Password123!");
        UserResponseDTO userDTO = new UserResponseDTO();
        userDTO.setId(1L);
        userDTO.setEmail("test@example.com");
        AuthResponseDTO response = new AuthResponseDTO("access-token-123", "refresh-token-456", userDTO);

        when(authService.login(any(AuthRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-456"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));

        verify(authService).login(any(AuthRequestDTO.class));
    }

    @Test
    @DisplayName("POST /api/auth/login - Invalid payload returns 400 Bad Request")
    void authenticateUser_ValidationFailure() throws Exception {
        AuthRequestDTO invalidRequest = new AuthRequestDTO("", ""); // missing required fields

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/auth/login - BadCredentialsException returns 401 Unauthorized")
    void authenticateUser_BadCredentials() throws Exception {
        AuthRequestDTO request = new AuthRequestDTO("wrong@example.com", "WrongPassword!");

        when(authService.login(any(AuthRequestDTO.class)))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/register - Success returns 200 with tokens")
    void registerUser_Success() throws Exception {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john.doe@example.com");
        request.setPassword("Password123!");
        request.setEmployeeCode("EMP001");
        request.setPhone("+1234567890");

        UserResponseDTO userDTO = new UserResponseDTO();
        userDTO.setId(2L);
        userDTO.setEmail("john.doe@example.com");
        AuthResponseDTO response = new AuthResponseDTO("access-token-new", "refresh-token-new", userDTO);

        when(authService.register(any(RegisterRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-new"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-new"));

        verify(authService).register(any(RegisterRequestDTO.class));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Success returns 200 with new tokens")
    void refreshToken_Success() throws Exception {
        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("valid-refresh-token");

        UserResponseDTO userDTO = new UserResponseDTO();
        userDTO.setId(1L);
        AuthResponseDTO response = new AuthResponseDTO("new-access-token", "new-refresh-token", userDTO);

        when(authService.refreshToken(any(TokenRefreshRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));

        verify(authService).refreshToken(any(TokenRefreshRequestDTO.class));
    }

    @Test
    @DisplayName("POST /api/auth/logout - Success returns 200")
    void logoutUser_Success() throws Exception {
        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("logout-refresh-token");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("User logged out successfully"));

        verify(authService).logout("logout-refresh-token");
    }

    @Test
    @DisplayName("POST /api/auth/change-password - Success returns 200")
    void changePassword_Success() throws Exception {
        PasswordChangeRequestDTO request = new PasswordChangeRequestDTO("OldPass123!", "NewPass123!");
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("user@example.com", null, Collections.emptyList());

        mockMvc.perform(post("/api/auth/change-password")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password changed successfully"));

        verify(authService).changePassword(eq("user@example.com"), any(PasswordChangeRequestDTO.class));
    }

    @Test
    @DisplayName("GET /api/auth/login-history - Returns paginated login history")
    void getMyLoginHistory_Success() throws Exception {
        LoginHistoryResponseDTO dto = new LoginHistoryResponseDTO();
        dto.setId(1L);
        dto.setUserEmail("user@example.com");
        dto.setIpAddress("127.0.0.1");
        dto.setStatus("SUCCESS");
        dto.setLoginTime(LocalDateTime.now());

        Page<LoginHistoryResponseDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(loginTrackingService.getLoginHistoryForCurrentUser(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/auth/login-history")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userEmail").value("user@example.com"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));

        verify(loginTrackingService).getLoginHistoryForCurrentUser(any(Pageable.class));
    }
}
