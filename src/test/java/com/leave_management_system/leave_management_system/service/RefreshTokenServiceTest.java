package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.entity.RefreshToken;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.TokenRefreshException;
import com.leave_management_system.leave_management_system.repository.RefreshTokenRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", 604800000L); // 7 days

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("tokenuser@example.com");
    }

    @Test
    @DisplayName("createRefreshToken: sets user, expiration, hash, and saves token")
    void createRefreshToken_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken token = refreshTokenService.createRefreshToken(1L, "raw-random-token-12345");

        assertNotNull(token);
        assertEquals(testUser, token.getUser());
        assertFalse(token.isRevoked());
        assertNotNull(token.getTokenHash());
        assertTrue(token.getExpiresAt().isAfter(LocalDateTime.now()));
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("findByToken: hashes input token and searches repository")
    void findByToken_Success() {
        RefreshToken token = new RefreshToken();
        token.setId(10L);
        token.setUser(testUser);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        Optional<RefreshToken> found = refreshTokenService.findByToken("my-raw-token");

        assertTrue(found.isPresent());
        assertEquals(10L, found.get().getId());
        verify(refreshTokenRepository, times(1)).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("verifyExpiration: returns token unchanged when valid and not expired")
    void verifyExpiration_ValidToken_ReturnsToken() {
        RefreshToken token = new RefreshToken();
        token.setExpiresAt(LocalDateTime.now().plusDays(2));
        token.setRevoked(false);

        RefreshToken result = refreshTokenService.verifyExpiration(token);

        assertSame(token, result);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("verifyExpiration: deletes token and throws TokenRefreshException when expired")
    void verifyExpiration_ExpiredToken_ThrowsException() {
        RefreshToken token = new RefreshToken();
        token.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        token.setRevoked(false);

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.verifyExpiration(token));
        verify(refreshTokenRepository, times(1)).delete(token);
    }

    @Test
    @DisplayName("verifyExpiration: deletes token and throws TokenRefreshException when revoked")
    void verifyExpiration_RevokedToken_ThrowsException() {
        RefreshToken token = new RefreshToken();
        token.setExpiresAt(LocalDateTime.now().plusDays(2));
        token.setRevoked(true);

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.verifyExpiration(token));
        verify(refreshTokenRepository, times(1)).delete(token);
    }

    @Test
    @DisplayName("deleteByUserId: deletes all tokens for user and returns count")
    void deleteByUserId_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(refreshTokenRepository.deleteByUser(testUser)).thenReturn(2);

        int count = refreshTokenService.deleteByUserId(1L);

        assertEquals(2, count);
        verify(refreshTokenRepository, times(1)).deleteByUser(testUser);
    }

    @Test
    @DisplayName("deleteByToken: deletes token if found by hash")
    void deleteByToken_Found_DeletesToken() {
        RefreshToken token = new RefreshToken();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        refreshTokenService.deleteByToken("token-to-delete");

        verify(refreshTokenRepository, times(1)).delete(token);
    }

    @Test
    @DisplayName("deleteByToken: does nothing if token not found")
    void deleteByToken_NotFound_NoDelete() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        refreshTokenService.deleteByToken("missing-token");

        verify(refreshTokenRepository, never()).delete(any());
    }
}

