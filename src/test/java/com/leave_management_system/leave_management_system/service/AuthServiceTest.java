package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.RefreshToken;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.exception.DuplicateResourceException;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.exception.TokenRefreshException;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private Role employeeRole;

    @BeforeEach
    void setUp() {
        employeeRole = new Role("EMPLOYEE");
        employeeRole.setId(1L);

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword123");
        testUser.setActive(true);
        testUser.getRoles().add(employeeRole);
    }

    @Test
    @DisplayName("login: successfully authenticates and returns JWT with refresh token")
    void login_Success() {
        AuthRequestDTO request = new AuthRequestDTO("test@example.com", "rawPassword123");
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(jwtUtils.generateJwtToken(auth)).thenReturn("mocked.jwt.token");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        AuthResponseDTO response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("test@example.com", response.getUser().getEmail());
        verify(refreshTokenService, times(1)).createRefreshToken(eq(1L), anyString());
    }

    @Test
    @DisplayName("login: throws BadCredentialsException when authentication manager rejects credentials")
    void login_InvalidCredentials_ThrowsException() {
        AuthRequestDTO request = new AuthRequestDTO("test@example.com", "wrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(jwtUtils, never()).generateJwtToken(any());
        verify(refreshTokenService, never()).createRefreshToken(anyLong(), anyString());
    }

    @Test
    @DisplayName("register: successfully registers employee, creates user, auto-logs in, and returns tokens")
    void register_Success() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail("newuser@example.com");
        request.setPassword("newPassword123");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setPhone("+1234567890");
        request.setEmployeeCode("EMP-999");

        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("newPassword123")).thenReturn("encodedSecret");
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(2L);
            return u;
        });

        Employee savedEmployee = new Employee();
        savedEmployee.setId(10L);
        when(employeeRepository.save(any(Employee.class))).thenReturn(savedEmployee);

        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtUtils.generateJwtToken(auth)).thenReturn("mock.registered.token");

        AuthResponseDTO response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock.registered.token", response.getAccessToken());
        assertEquals("newuser@example.com", response.getUser().getEmail());
        verify(employeeRepository, times(1)).save(any(Employee.class));
        verify(auditService, times(1)).logAction(eq("EMPLOYEE_CREATED"), eq("Employee"), any(), isNull(), anyString());
    }

    @Test
    @DisplayName("register: throws DuplicateResourceException when email is already registered")
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail("existing@example.com");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("refreshToken: successfully issues a new JWT and rotates refresh token")
    void refreshToken_Success() {
        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("old-refresh-token");

        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(testUser);
        storedToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        storedToken.setRevoked(false);

        when(refreshTokenService.findByToken("old-refresh-token")).thenReturn(Optional.of(storedToken));
        when(refreshTokenService.verifyExpiration(storedToken)).thenReturn(storedToken);
        when(jwtUtils.generateTokenFromUsername("test@example.com")).thenReturn("new.refreshed.jwt");

        AuthResponseDTO response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new.refreshed.jwt", response.getAccessToken());
        verify(refreshTokenService, times(1)).deleteByToken("old-refresh-token");
        verify(refreshTokenService, times(1)).createRefreshToken(eq(1L), anyString());
    }

    @Test
    @DisplayName("refreshToken: throws TokenRefreshException when token is not found in database")
    void refreshToken_NotFound_ThrowsException() {
        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("missing-token");

        when(refreshTokenService.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThrows(TokenRefreshException.class, () -> authService.refreshToken(request));
        verify(refreshTokenService, never()).deleteByToken(anyString());
    }

    @Test
    @DisplayName("logout: deletes refresh token and logs security event")
    void logout_Success() {
        authService.logout("raw-token-to-delete");

        verify(refreshTokenService, times(1)).deleteByToken("raw-token-to-delete");
        verify(auditService, times(1)).logSecurityEvent(any(), isNull(), isNull(), anyString());
    }

    @Test
    @DisplayName("changePassword: successfully changes password, encodes it, and revokes tokens")
    void changePassword_Success() {
        PasswordChangeRequestDTO request = new PasswordChangeRequestDTO();
        request.setCurrentPassword("oldPass");
        request.setNewPassword("newPassStrong123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPass", "encodedPassword123")).thenReturn(true);
        when(passwordEncoder.matches("newPassStrong123", "encodedPassword123")).thenReturn(false);
        when(passwordEncoder.encode("newPassStrong123")).thenReturn("newEncodedPass123");

        authService.changePassword("test@example.com", request);

        assertEquals("newEncodedPass123", testUser.getPassword());
        verify(userRepository, times(1)).save(testUser);
        verify(refreshTokenService, times(1)).deleteByUserId(1L);
        verify(auditService, times(1)).logAction(eq("PASSWORD_CHANGED"), eq("User"), eq(1L), isNull(), anyString());
    }

    @Test
    @DisplayName("changePassword: throws ResourceNotFoundException if user does not exist")
    void changePassword_UserNotFound_ThrowsException() {
        PasswordChangeRequestDTO request = new PasswordChangeRequestDTO();
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.changePassword("unknown@example.com", request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword: throws IllegalArgumentException when current password does not match")
    void changePassword_IncorrectCurrentPassword_ThrowsException() {
        PasswordChangeRequestDTO request = new PasswordChangeRequestDTO();
        request.setCurrentPassword("wrongPass");
        request.setNewPassword("newPass");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPass", "encodedPassword123")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword("test@example.com", request));
        assertEquals("Current password is incorrect", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword: throws IllegalArgumentException when new password matches current password")
    void changePassword_SamePassword_ThrowsException() {
        PasswordChangeRequestDTO request = new PasswordChangeRequestDTO();
        request.setCurrentPassword("oldPass");
        request.setNewPassword("oldPass");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPass", "encodedPassword123")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changePassword("test@example.com", request));
        assertEquals("New password cannot be the same as current password", ex.getMessage());
        verify(userRepository, never()).save(any());
    }
}
