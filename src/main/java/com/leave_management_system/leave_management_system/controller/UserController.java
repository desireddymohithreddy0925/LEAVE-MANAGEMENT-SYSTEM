package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.UserResponseDTO;
import com.leave_management_system.leave_management_system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Endpoints for managing user accounts, security lockouts, and status")
@SecurityRequirement(name = "bearerAuth")
@org.springframework.validation.annotation.Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE') or hasAuthority('USER_LOCK_MANAGE')")
    @Operation(summary = "List users", description = "Retrieve paginated list of users with optional filtering by lockout status")
    public ResponseEntity<Page<UserResponseDTO>> getAllUsers(
            @RequestParam(required = false) Boolean locked,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(locked, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE') or hasAuthority('USER_LOCK_MANAGE') or @securityService.isSelf(authentication, #id)")
    @Operation(summary = "Get user by ID", description = "Retrieve details for a specific user")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE') or hasAuthority('USER_LOCK_MANAGE')")
    @Operation(summary = "Unlock user account", description = "Unlocks a locked user account and resets consecutive failed login attempts")
    public ResponseEntity<UserResponseDTO> unlockUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.unlockUser(id));
    }

    @PutMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SECURITY_EVENT_MANAGE') or hasAuthority('USER_LOCK_MANAGE')")
    @Operation(summary = "Lock user account", description = "Manually locks a user account for security reasons")
    public ResponseEntity<UserResponseDTO> lockUser(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(userService.lockUser(id, reason));
    }
}
