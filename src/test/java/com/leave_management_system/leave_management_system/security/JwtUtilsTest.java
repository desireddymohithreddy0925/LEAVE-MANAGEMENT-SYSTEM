package com.leave_management_system.leave_management_system.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        // 32 bytes encoded in Base64 for HS256 key
        String secret32Bytes = "12345678901234567890123456789012";
        String base64Secret = Base64.getEncoder().encodeToString(secret32Bytes.getBytes(StandardCharsets.UTF_8));

        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", base64Secret);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000); // 1 hour
    }

    @Test
    @DisplayName("generateTokenFromUsername and getUserNameFromJwtToken: round-trip preserves username")
    void generateAndParseToken_Success() {
        String token = jwtUtils.generateTokenFromUsername("testuser@example.com");

        assertNotNull(token);
        assertFalse(token.isBlank());

        String username = jwtUtils.getUserNameFromJwtToken(token);
        assertEquals("testuser@example.com", username);
    }

    @Test
    @DisplayName("generateJwtToken: extracts username from Authentication principal")
    void generateJwtToken_FromAuthentication_Success() {
        UserDetails principal = new User("authuser@example.com", "password", Collections.emptyList());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        String token = jwtUtils.generateJwtToken(auth);

        assertNotNull(token);
        assertEquals("authuser@example.com", jwtUtils.getUserNameFromJwtToken(token));
    }

    @Test
    @DisplayName("validateJwtToken: succeeds for valid token")
    void validateJwtToken_ValidToken_DoesNotThrow() {
        String token = jwtUtils.generateTokenFromUsername("valid@example.com");

        assertDoesNotThrow(() -> jwtUtils.validateJwtToken(token));
    }

    @Test
    @DisplayName("validateJwtToken: throws Exception for malformed or invalid token")
    void validateJwtToken_MalformedToken_ThrowsException() {
        assertThrows(Exception.class, () -> jwtUtils.validateJwtToken("invalid.token.payload"));
    }

    @Test
    @DisplayName("validateJwtToken: throws Exception for expired token")
    void validateJwtToken_ExpiredToken_ThrowsException() {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1000); // already expired
        String expiredToken = jwtUtils.generateTokenFromUsername("expired@example.com");

        assertThrows(Exception.class, () -> jwtUtils.validateJwtToken(expiredToken));
    }
}

