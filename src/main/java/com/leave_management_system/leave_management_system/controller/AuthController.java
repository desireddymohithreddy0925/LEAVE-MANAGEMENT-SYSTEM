package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.AuthRequestDTO;
import com.leave_management_system.leave_management_system.dto.AuthResponseDTO;
import com.leave_management_system.leave_management_system.dto.RegisterRequestDTO;
import com.leave_management_system.leave_management_system.dto.TokenRefreshRequestDTO;
import com.leave_management_system.leave_management_system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication API", description = "Endpoints for user authentication and registration")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Logs in a user and returns a JWT and Refresh Token")
    public ResponseEntity<AuthResponseDTO> authenticateUser(@Valid @RequestBody AuthRequestDTO loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Registers a new employee and returns a JWT and Refresh Token")
    public ResponseEntity<AuthResponseDTO> registerUser(@Valid @RequestBody RegisterRequestDTO registerRequest) {
        return ResponseEntity.ok(authService.register(registerRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token", description = "Gets a new JWT using a valid refresh token")
    public ResponseEntity<AuthResponseDTO> refreshToken(@Valid @RequestBody TokenRefreshRequestDTO request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Logs out a user by invalidating their refresh token. Note: Client-side access JWTs will remain valid until their expiration (stateless).")
    public ResponseEntity<String> logoutUser(@Valid @RequestBody TokenRefreshRequestDTO request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok("User logged out successfully");
    }
}
