package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.RegisterRequestDTO;
import com.leave_management_system.leave_management_system.dto.TokenRefreshRequestDTO;
import com.leave_management_system.leave_management_system.dto.AuthResponseDTO;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    private AuthResponseDTO registerAndLogin(String email, String password) throws Exception {
        RegisterRequestDTO register = new RegisterRequestDTO();
        register.setFirstName("Test");
        register.setLastName("User");
        register.setEmail(email);
        register.setPassword(password);
        register.setPhone("1234567890");
        register.setEmployeeCode("EMP-" + System.currentTimeMillis());

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponseDTO.class);
    }

    @Test
    void testSuccessfulLogout() throws Exception {
        AuthResponseDTO auth = registerAndLogin("logout@test.com", "password123");

        TokenRefreshRequestDTO logoutReq = new TokenRefreshRequestDTO();
        logoutReq.setRefreshToken(auth.getRefreshToken());

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + auth.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isOk());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username="testuser", roles="EMPLOYEE")
    void testLogoutWithInvalidRefreshToken() throws Exception {
        TokenRefreshRequestDTO logoutReq = new TokenRefreshRequestDTO();
        logoutReq.setRefreshToken("invalid-token-123");

        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isOk()); // We don't throw error if token not found on logout, it's idempotent. Wait, does our implementation throw? Let's check.
                // Our implementation is findByToken(token).ifPresent(delete) which does not throw.
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username="testuser", roles="EMPLOYEE")
    void testLogoutWithBlankRefreshToken() throws Exception {
        TokenRefreshRequestDTO logoutReq = new TokenRefreshRequestDTO();
        logoutReq.setRefreshToken("");

        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isBadRequest()); // Validations will fail
    }

    @Test
    void testRefreshAfterLogoutMustFail() throws Exception {
        AuthResponseDTO auth = registerAndLogin("refresh.logout@test.com", "password123");

        TokenRefreshRequestDTO req = new TokenRefreshRequestDTO();
        req.setRefreshToken(auth.getRefreshToken());

        // Logout
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Refresh should fail
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is5xxServerError()); 
    }

    @Test
    void testRefreshTokenRotation() throws Exception {
        AuthResponseDTO auth = registerAndLogin("rotate@test.com", "password123");

        TokenRefreshRequestDTO req = new TokenRefreshRequestDTO();
        req.setRefreshToken(auth.getRefreshToken());

        // Refresh
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponseDTO newAuth = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), AuthResponseDTO.class);
        
        assert !newAuth.getRefreshToken().equals(auth.getRefreshToken()) : "Refresh token must be rotated";

        // Old token should not be reusable
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void testExpiredRefreshToken() throws Exception {
        AuthResponseDTO auth = registerAndLogin("expired@test.com", "password123");

        // Manually update the token to be expired in the DB
        com.leave_management_system.leave_management_system.service.RefreshTokenService refreshTokenService = context.getBean(com.leave_management_system.leave_management_system.service.RefreshTokenService.class);
        com.leave_management_system.leave_management_system.repository.RefreshTokenRepository refreshTokenRepository = context.getBean(com.leave_management_system.leave_management_system.repository.RefreshTokenRepository.class);
        com.leave_management_system.leave_management_system.entity.RefreshToken rt = refreshTokenService.findByToken(auth.getRefreshToken()).orElseThrow();
        rt.setExpiresAt(java.time.LocalDateTime.now().minusDays(1));
        refreshTokenRepository.save(rt);

        TokenRefreshRequestDTO req = new TokenRefreshRequestDTO();
        req.setRefreshToken(auth.getRefreshToken());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is5xxServerError()); 
    }

    @Test
    void testRevokedRefreshToken() throws Exception {
        AuthResponseDTO auth = registerAndLogin("revoked@test.com", "password123");

        // Manually update the token to be revoked in the DB
        com.leave_management_system.leave_management_system.service.RefreshTokenService refreshTokenService = context.getBean(com.leave_management_system.leave_management_system.service.RefreshTokenService.class);
        com.leave_management_system.leave_management_system.repository.RefreshTokenRepository refreshTokenRepository = context.getBean(com.leave_management_system.leave_management_system.repository.RefreshTokenRepository.class);
        com.leave_management_system.leave_management_system.entity.RefreshToken rt = refreshTokenService.findByToken(auth.getRefreshToken()).orElseThrow();
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);

        TokenRefreshRequestDTO req = new TokenRefreshRequestDTO();
        req.setRefreshToken(auth.getRefreshToken());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is5xxServerError()); 
    }
}
