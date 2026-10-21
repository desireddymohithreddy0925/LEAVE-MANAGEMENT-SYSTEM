package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.ReviewStatus;
import com.leave_management_system.leave_management_system.entity.RiskLevel;
import com.leave_management_system.leave_management_system.entity.TransactionType;
import com.leave_management_system.leave_management_system.service.TransactionReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/transaction-reviews")
@Tag(name = "Transaction Review API", description = "Endpoints for reviewing, monitoring, and resolving sensitive leave and balance transactions")
@Validated
public class TransactionReviewController {

    private final TransactionReviewService transactionReviewService;

    public TransactionReviewController(TransactionReviewService transactionReviewService) {
        this.transactionReviewService = transactionReviewService;
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_HR') or hasAuthority('AUDIT_VIEW') or hasAuthority('ADMIN_AUDIT_VIEW')")
    @GetMapping
    @Operation(summary = "Search transaction reviews", description = "Search and filter transaction reviews with pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    })
    public Page<TransactionReviewResponseDTO> searchTransactionReviews(
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false) ReviewStatus reviewStatus,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) Long reviewerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionReviewService.searchTransactionReviews(transactionType, reviewStatus, riskLevel,
                employeeId, actorEmail, reviewerId, startDate, endDate, pageable);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_HR') or hasAuthority('AUDIT_VIEW') or hasAuthority('ADMIN_AUDIT_VIEW')")
    @GetMapping("/summary")
    @Operation(summary = "Get transaction review summary", description = "Retrieve aggregate metrics, risk distribution, and completion rates.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved summary")
    })
    public TransactionReviewSummaryDTO getSummaryMetrics() {
        return transactionReviewService.getSummaryMetrics();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    @Operation(summary = "Get my transaction reviews", description = "Retrieve transactions for the authenticated employee or actor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved personal transaction reviews")
    })
    public Page<TransactionReviewResponseDTO> getMyTransactionReviews(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionReviewService.getMyTransactionReviews(authentication.getName(), pageable);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_HR') or hasAuthority('AUDIT_VIEW') or hasAuthority('ADMIN_AUDIT_VIEW') or @securityService.canViewEmployee(authentication, #employeeId)")
    @GetMapping("/employee/{employeeId}")
    @Operation(summary = "Get transaction reviews for employee", description = "Retrieve transactions associated with a specific employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved employee transaction reviews")
    })
    public Page<TransactionReviewResponseDTO> getEmployeeTransactionReviews(
            @PathVariable Long employeeId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionReviewService.getEmployeeTransactionReviews(employeeId, pageable);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_HR') or hasAuthority('AUDIT_VIEW') or hasAuthority('ADMIN_AUDIT_VIEW') or @securityService.canViewTransactionReview(authentication, #id)")
    @GetMapping("/{id}")
    @Operation(summary = "Get transaction review by ID", description = "Retrieve a single transaction review record.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved transaction review"),
            @ApiResponse(responseCode = "404", description = "Transaction review not found")
    })
    public TransactionReviewResponseDTO getTransactionReviewById(@PathVariable Long id) {
        return transactionReviewService.getTransactionReviewById(id);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_HR') or hasAuthority('AUDIT_VIEW') or hasAuthority('ADMIN_AUDIT_VIEW')")
    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign reviewer", description = "Assign an auditor or reviewer to a transaction review.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully assigned reviewer"),
            @ApiResponse(responseCode = "404", description = "Review or reviewer not found")
    })
    public TransactionReviewResponseDTO assignReviewer(
            @PathVariable Long id,
            @RequestBody(required = false) AssignReviewerDTO dto,
            Authentication authentication) {
        String currentUsername = authentication != null ? authentication.getName() : null;
        return transactionReviewService.assignReviewer(id, dto, currentUsername);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_HR') or hasAuthority('AUDIT_VIEW') or hasAuthority('ADMIN_AUDIT_VIEW')")
    @PostMapping("/{id}/review")
    @Operation(summary = "Submit review decision", description = "Submit an audit review decision, resolution, and comments.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully submitted review"),
            @ApiResponse(responseCode = "404", description = "Transaction review not found")
    })
    public TransactionReviewResponseDTO submitReview(
            @PathVariable Long id,
            @Valid @RequestBody ReviewDecisionDTO dto,
            Authentication authentication) {
        String currentUsername = authentication != null ? authentication.getName() : null;
        return transactionReviewService.submitReview(id, dto, currentUsername);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_HR') or hasAuthority('AUDIT_VIEW') or hasAuthority('ADMIN_AUDIT_VIEW')")
    @PostMapping("/{id}/flag")
    @Operation(summary = "Flag transaction", description = "Flag a transaction for audit scrutiny or escalation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully flagged transaction"),
            @ApiResponse(responseCode = "404", description = "Transaction review not found")
    })
    public TransactionReviewResponseDTO flagTransaction(
            @PathVariable Long id,
            @Valid @RequestBody FlagTransactionDTO dto,
            Authentication authentication) {
        String currentUsername = authentication != null ? authentication.getName() : null;
        return transactionReviewService.flagTransaction(id, dto, currentUsername);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_HR') or hasAuthority('AUDIT_VIEW') or hasAuthority('ADMIN_AUDIT_VIEW')")
    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve flagged review", description = "Resolve a flagged transaction review with documented resolution.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully resolved flagged review"),
            @ApiResponse(responseCode = "404", description = "Transaction review not found")
    })
    public TransactionReviewResponseDTO resolveReview(
            @PathVariable Long id,
            @Valid @RequestBody ResolveReviewDTO dto,
            Authentication authentication) {
        String currentUsername = authentication != null ? authentication.getName() : null;
        return transactionReviewService.resolveReview(id, dto, currentUsername);
    }
}
