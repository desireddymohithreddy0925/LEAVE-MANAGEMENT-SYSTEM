package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.LoginHistoryResponseDTO;
import com.leave_management_system.leave_management_system.dto.LoginSummaryDTO;
import com.leave_management_system.leave_management_system.service.LoginTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/login-history")
@Tag(name = "Login Tracking API", description = "Endpoints for tracking user login history, attempts, and statistics")
@org.springframework.validation.annotation.Validated
public class LoginHistoryController {

    private final LoginTrackingService loginTrackingService;

    public LoginHistoryController(LoginTrackingService loginTrackingService) {
        this.loginTrackingService = loginTrackingService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    @Operation(summary = "Get current user login history", description = "Retrieve paginated login attempts for the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved login history")
    })
    public Page<LoginHistoryResponseDTO> getMyLoginHistory(
            @PageableDefault(size = 20, sort = "loginTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return loginTrackingService.getLoginHistoryForCurrentUser(pageable);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/summary")
    @Operation(summary = "Get current user login summary", description = "Retrieve login metrics and last login timestamp for the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved login summary")
    })
    public LoginSummaryDTO getMyLoginSummary() {
        return loginTrackingService.getCurrentUserSummary();
    }

    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")
    @GetMapping
    @Operation(summary = "Search all login history", description = "Administrative search across all user login attempts with filtering and pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved login history")
    })
    public Page<LoginHistoryResponseDTO> searchLoginHistory(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "loginTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return loginTrackingService.searchLoginHistory(userId, email, status, startDate, endDate, pageable);
    }

    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get login history for user", description = "Administrative retrieval of a specific user's login history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user login history"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public Page<LoginHistoryResponseDTO> getUserLoginHistory(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "loginTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return loginTrackingService.getLoginHistoryForUser(userId, pageable);
    }

    @PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")
    @GetMapping("/user/{userId}/summary")
    @Operation(summary = "Get login summary for user", description = "Administrative retrieval of a specific user's login summary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved user login summary"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public LoginSummaryDTO getUserLoginSummary(@PathVariable Long userId) {
        return loginTrackingService.getUserSummary(userId);
    }
}
