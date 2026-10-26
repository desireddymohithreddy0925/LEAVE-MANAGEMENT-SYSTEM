# Requirement Traceability Matrix

This document maps the mandatory internship requirements to their specific implementations, APIs, and automated tests. It serves as proof that all business requirements have been fully implemented and verified.

---

### 1. Leave Day Calculation
- **Requirement:** Saturdays and Sundays are properly excluded from the total leave day count.
- **Implementation:** `LeaveRequestService.calculateWorkingDays()`
- **API:** `POST /api/leave-requests` and `PUT /api/leave-requests/{id}/approve`
- **Test:** `LeaveRequestServiceTest.createLeaveRequest_WeekendEdgeCases`
- **Status:** **DONE**

### 2. Leave Overlap Detection & Pre-Flight API
- **Requirement:** Conflicting overlapping requests must be prevented. Employees cannot apply for leave if dates overlap with existing `PENDING` or `APPROVED` leaves across any leave types. Pre-flight overlap query capabilities must allow employees and managers to evaluate date ranges before submission.
- **Implementation:** Interval overlap detection (`startDate <= :endDate AND endDate >= :startDate`) in `LeaveRequestRepository` (`hasOverlappingLeave`, `findOverlappingLeaves`, `hasOverlappingLeaveExcludingId`, `findOverlappingLeavesExcludingId`), enforced in `LeaveRequestService.createLeaveRequest()` and exposed via `LeaveRequestService.checkOverlap()`.
- **API:** `POST /api/leave-requests`, `GET /api/leave-requests/check-overlap?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD[&employeeId=ID]`
- **Test:** `LeaveRequestRepositoryTest.testOverlapEdgeCases`, `testFindOverlappingLeaves_ReturnsMatchingEntities`, `testFindOverlappingLeavesExcludingId`, `testOverlap_DifferentEmployees_NoOverlap`, `LeaveRequestServiceTest.createLeaveRequest_OverlappingLeave_ThrowsException`, `checkOverlap_*`, `LeaveRequestControllerIntegrationTest.checkOverlap_*`, `createLeaveRequest_DifferentLeaveTypeOverlapping_Returns400BadRequest`, `createLeaveRequest_AdjacentDates_SuccessNoOverlap`, and `LeaveRequestServiceConcurrencyIntegrationTest.testConcurrentLeaveApplicationsThrowsOptimisticLockException`.
- **Status:** **DONE**

### 3. Employee Eligibility
- **Requirement:** Only `ACTIVE` employees can apply for leave.
- **Implementation:** `LeaveRequestService.createLeaveRequest()` (`!employee.isActive()` check)
- **API:** `POST /api/leave-requests`
- **Test:** `LeaveRequestServiceTest.createLeaveRequest_InactiveEmployee_ThrowsException`
- **Status:** **DONE**

### 4. Active Leave Types & Management
- **Requirement:** Employees can only apply for active leave types; leave types can be filtered by active status and are protected against deletion if in use.
- **Implementation:** `LeaveRequestService.createLeaveRequest()` (`!leaveType.isActive()` check), `LeaveTypeService.getAllLeaveTypes(Boolean active)`, and `LeaveTypeService.deleteLeaveType()` (reference validation with `leaveBalanceRepository` and `leaveRequestRepository`).
- **API:** `POST /api/leave-requests`, `GET /api/leave-types?active=true`, `DELETE /api/leave-types/{id}`
- **Test:** `LeaveRequestServiceTest.createLeaveRequest_InactiveLeaveType_ThrowsException`, `LeaveTypeServiceTest.deleteLeaveType_HasLeaveBalances_ThrowsException`, `LeaveTypeControllerIntegrationTest.getAllLeaveTypes_Authenticated_And_FilterActive`, `LeaveTypeControllerIntegrationTest.deleteLeaveType_WithExistingBalance_Fails`
- **Status:** **DONE**

### 5. State Machine Handling & Leave Approval
- **Requirement:** Validated transitions (e.g., `PENDING` → `APPROVED` / `REJECTED` / `CANCELLED`). Invalid transitions are blocked (e.g., double approval, double cancellation, approving rejected/cancelled requests). Pre-approval verification ensures no overlapping approved leaves exist for the employee before balance deduction. Strict balance deduction on approval locks annual balance record and enforces single calendar year isolation.
- **Implementation:** Handled inside `approveLeaveRequest`, `rejectLeaveRequest`, and `cancelLeaveRequest` in `LeaveRequestService`, supporting dual `PUT` and `PATCH` methods on `/{id}/approve`, `/{id}/reject`, and `/{id}/cancel`. Scoped by `@PreAuthorize("@securityService.canApproveLeaveRequest(authentication, #id)")` to enforce hierarchical and administrative authorization. Emits `@AuditAction` events on approval.
- **API:** `PUT/PATCH /api/leave-requests/{id}/approve`, `PUT/PATCH /api/leave-requests/{id}/reject`, `PUT/PATCH /api/leave-requests/{id}/cancel`
- **Test:** `LeaveRequestServiceTest` (`approveLeaveRequest_Success`, `approveLeaveRequest_NotPending_ThrowsException`, `approveLeaveRequest_AlreadyRejected_ThrowsException`, `approveLeaveRequest_AlreadyCancelled_ThrowsException`, `approveLeaveRequest_OverlappingApprovedLeaveExists_ThrowsException`, `approveLeaveRequest_InsufficientBalance_ThrowsException`, `approveLeaveRequest_NotFound_ThrowsException`, `cancelLeaveRequest_Rejected_ThrowsException`), `LeaveRequestControllerIntegrationTest` (`approveLeaveRequest_Success`, `approveLeaveRequest_ViaPatch_Success`, `approveLeaveRequest_DoubleApproval_Returns400BadRequest`, `approveLeaveRequest_AlreadyRejected_Returns400BadRequest`, `approveLeaveRequest_AlreadyCancelled_Returns400BadRequest`, `approveLeaveRequest_OverlappingApprovedLeaveAlreadyExists_Returns400BadRequest`, `approveLeaveRequest_UnauthorizedUser_Returns403Forbidden`, `approveLeaveRequest_NotFound_Returns404NotFound`, `cancelLeaveRequest_ApprovedLeave_RestoresBalance_AndDoubleCancelFails`).
- **Status:** **DONE**

### 6. Leave Balance Management & Deduction
- **Requirement:** Track employee balances annually with self-service retrieval, automatic default initialization for active leave types, balance consistency enforcement on creation and updates (`total >= used + available`), deletion protection with pending/approved requests, strict annual leave balance isolation (leave requests strictly consume the balance for their applicable leave year only), cross-calendar-year leave request rejection (prohibiting requests spanning multiple calendar years to preserve annual quota integrity), and synchronized deduction/restoration on approval and cancellation.
- **Implementation:** `LeaveBalanceService` (`createLeaveBalance`, `getBalancesByEmployee`, `initializeBalancesForEmployee`, `updateLeaveBalance`, `deleteLeaveBalance`), `LeaveBalanceController` (`GET /api/leave-balances/me`, `POST /api/leave-balances/employee/{id}/initialize`, `POST /api/leave-balances`, `PUT /api/leave-balances/{id}`), and `LeaveRequestService` (`createLeaveRequest`, `approveLeaveRequest`, `cancelLeaveRequest` strictly enforcing single calendar year boundary and using `findByEmployeeAndLeaveTypeAndYearWithLock`).
- **API:** `GET /api/leave-balances/me?year=YYYY`, `GET /api/leave-balances/employee/{employeeId}?year=YYYY`, `POST /api/leave-balances/employee/{employeeId}/initialize`, `POST /api/leave-balances`, `PUT /api/leave-balances/{id}`, `DELETE /api/leave-balances/{id}`
- **Test:** `LeaveBalanceServiceTest` (35 unit tests), `LeaveBalanceControllerIntegrationTest` (26 integration tests), `LeaveRequestServiceTest` (33 unit tests), `LeaveRequestControllerIntegrationTest` (16 integration tests), `LeaveRequestServiceConcurrencyIntegrationTest` (3 concurrency tests).
- **Status:** **DONE**

### 7. Rejection Reasons & Leave Rejection Lifecycle
- **Requirement:** Managers/approvers must supply a mandatory, non-blank rejection reason when rejecting a leave request, which is persisted to the database. Validates that reason is trimmed and does not exceed 500 characters. Supports flexible submission via query parameter or JSON request body (`RejectRequestDTO`). Enforces RBAC permissions (`LEAVE_REJECT`), hierarchical manager/admin scoping, blocks double rejection and invalid state transitions (from `APPROVED` or `CANCELLED`), leaves employee balances untouched, and immediately frees up calendar dates for re-application without overlap conflicts.
- **Implementation:** `LeaveRequestService.rejectLeaveRequest()`, `LeaveRequestController.rejectLeaveRequest()` supporting `PUT`, `PATCH`, and `POST` methods, `SecurityAuthorizationService.canRejectLeaveRequest()`, `RejectRequestDTO`, and Liquibase migration `015-modify-rejection-reason-length.yaml` (expanding `rejection_reason` to `varchar(500)`). Emits `@AuditAction(action = "LEAVE_REJECTED", entityType = "LeaveRequest")`.
- **API:** `PUT/PATCH/POST /api/leave-requests/{id}/reject?reason=...` and `PUT/PATCH/POST /api/leave-requests/{id}/reject` (with JSON body `{"reason": "..."}`)
- **Test:** `LeaveRequestServiceTest` (`rejectLeaveRequest_Success`, `rejectLeaveRequest_BlankReason_ThrowsException`, `rejectLeaveRequest_NullReason_ThrowsException`, `rejectLeaveRequest_ReasonExceeds500Characters_ThrowsException`, `rejectLeaveRequest_AlreadyApproved_ThrowsException`, `rejectLeaveRequest_AlreadyCancelled_ThrowsException`, `rejectLeaveRequest_AlreadyRejected_ThrowsException`, `rejectLeaveRequest_NotFound_ThrowsException`, `rejectLeaveRequest_TrimsWhitespaceFromReason`), `LeaveRequestControllerIntegrationTest` (`rejectLeaveRequest_MandatoryReason_Success`, `rejectLeaveRequest_ViaPostWithJsonBody_Success`, `rejectLeaveRequest_DoubleRejection_Returns400BadRequest`, `rejectLeaveRequest_AlreadyApproved_Returns400BadRequest`, `rejectLeaveRequest_AlreadyCancelled_Returns400BadRequest`, `rejectLeaveRequest_BlankReasonInBody_Returns400BadRequest`, `rejectLeaveRequest_ReasonExceeds500Characters_Returns400BadRequest`, `rejectLeaveRequest_UnauthorizedUser_Returns403Forbidden`, `rejectLeaveRequest_NotFound_Returns404NotFound`, `rejectLeaveRequest_LeavesBalanceUntouched`, `rejectLeaveRequest_ReapplyOnSameDatesAfterRejection_Success`), `ValidationIntegrationTest` (`rejectRequest_BlankReason_Returns400`, `rejectRequest_OverlongReason_Returns400`).
- **Status:** **DONE**

### 7a. Leave Cancellation Lifecycle
- **Requirement:** Employees can cancel their own `PENDING` or `APPROVED` leave requests. Cancellation of `REJECTED` or already-`CANCELLED` requests is blocked. Cancelling an `APPROVED` leave atomically restores the annual leave balance (`available` incremented, `used` decremented, clamped to zero floor). Cancelling a `PENDING` leave never touches the balance. Cross-year approved leave cancellations are rejected. RBAC: Admin/HR can cancel any request; regular employees (with `LEAVE_CANCEL`) can only cancel their **own** requests.
- **Implementation:** `LeaveRequestService.cancelLeaveRequest()`, `LeaveRequestController.cancelLeaveRequest()` (`PUT`, `PATCH`, `POST`), `SecurityAuthorizationService.canCancelLeaveRequest()`. Balance restoration via `findByEmployeeAndLeaveTypeAndYearWithLock` with `OPTIMISTIC_FORCE_INCREMENT`. Emits `@AuditAction(action = "LEAVE_CANCELLED", entityType = "LeaveRequest")`.
- **API:** `PUT/PATCH/POST /api/leave-requests/{id}/cancel`
- **Test:** `LeaveRequestServiceTest` (`cancelLeaveRequest_Pending_Success`, `cancelLeaveRequest_Approved_Success`, `cancelLeaveRequest_Rejected_ThrowsException`, `cancelLeaveRequest_AlreadyCancelled_ThrowsException`, `cancelLeaveRequest_ApproveThenCancel_BalanceRestored`, `cancelLeaveRequest_Regression_MissingApplicableYearBalance_ThrowsException`, `cancelLeaveRequest_Regression_CrossCalendarYearDates_ThrowsException`, `cancelLeaveRequest_NotFound_ThrowsResourceNotFoundException`, `cancelLeaveRequest_Pending_DoesNotInteractWithBalance`, `cancelLeaveRequest_Approved_NullUsedBalance_DefaultsToZeroBeforeRestoration`), `LeaveRequestControllerIntegrationTest` (`cancelLeaveRequest_ApprovedLeave_RestoresBalance_AndDoubleCancelFails`, `cancelLeaveRequest_UnauthorizedUser_Returns403Forbidden`, `cancelLeaveRequest_PendingLeave_BalanceNotDeducted`, `cancelLeaveRequest_RejectedLeave_Returns400BadRequest`, `cancelLeaveRequest_SelfService_EmployeeCancelsOwnPendingLeave_Success`).
- **Status:** **DONE**

### 8. Employee Search
- **Requirement:** API supports searching employees by name/email.
- **Implementation:** `EmployeeRepository.searchEmployees()` called in `EmployeeService.searchEmployees()`
- **API:** `GET /api/employees?search=xyz`
- **Test:** `EmployeeServiceTest.searchEmployees_Success`
- **Status:** **DONE**

### 9. Department Transfers & Deletion Protection
- **Requirement:** Employees can transfer departments; departments with active employees cannot be deleted.
- **Implementation:** `EmployeeService.updateEmployee()` and `DepartmentService.deleteDepartment()`
- **API:** `PUT /api/employees/{id}` and `DELETE /api/departments/{id}`
- **Test:** `DepartmentServiceTest.deleteDepartment_WithEmployees_ThrowsException`
- **Status:** **DONE**

### 10. Searching, Filtering & Pagination
- **Requirement:** Support dynamic filtering by employee, status, date range, department, year, and pagination for both all requests and self-service history.
- **Implementation:** JPA `Specification<LeaveRequest>` in `LeaveRequestService.searchLeaveRequests()` and `getMyLeaveRequests()`
- **API:** `GET /api/leave-requests?departmentId=...&page=0&size=10`, `GET /api/leave-requests/me?status=...&year=...`
- **Test:** `LeaveRequestServiceTest.searchLeaveRequests_WithDepartmentId_Success`, `LeaveRequestServiceTest.getMyLeaveRequests_Success`, `LeaveRequestControllerIntegrationTest.searchLeaveRequests_WithDepartmentFilter_ReturnsMatchingDepartmentOnly`, `LeaveRequestControllerIntegrationTest.getMyLeaveRequests_ReturnsOnlyOwnLeaves_WithStatusAndYearFilter`
- **Status:** **DONE**

### 11. DTO Pattern & Validation Architecture
- **Requirement:** Strict use of Request/Response DTOs, Jakarta Bean Validation (`@Valid`, `@Validated`), custom domain constraint validators (`@ValidDateRange`, `@ValidLeaveBalance`, `@ValidStatus`), field-level bounds (`@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`, `@Positive`), controller path/param validation, and detailed error responses (`fieldErrors`).
- **Implementation:** Custom constraint validators in `com.leave_management_system.leave_management_system.validation`, annotated DTOs (`LeaveRequestDTO`, `LeaveBalanceRequestDTO`, `EmployeeRequestDTO`, `LeaveTypeRequestDTO`, `DepartmentRequestDTO`, `RejectRequestDTO`, `PasswordChangeRequestDTO`), `@Validated` on controllers, and enhanced `GlobalExceptionHandler` handling `MethodArgumentNotValidException`, `ConstraintViolationException`, and `HandlerMethodValidationException`.
- **API:** Enforced across all HTTP endpoints (returns HTTP `400 Bad Request` with structured error details and `fieldErrors`).
- **Test:** `ValidationIntegrationTest` (18 dedicated integration tests) covering date range boundaries, cross-year violations, balance consistency (`available > total`), status validity, mandatory reason checks, and negative bounds; plus DTO validation tests across all controller integration test suites.
- **Status:** **DONE**

### 12. Exception Handling (Clean Architecture)
- **Requirement:** Consistent JSON responses via `@RestControllerAdvice`.
- **Implementation:** `GlobalExceptionHandler.java` capturing standard and custom exceptions.
- **API:** All endpoints return structured `{error, message, status, timestamp}` on failure.
- **Test:** `LeaveRequestControllerIntegrationTest.createLeaveRequest_InvalidDates_Throws400`
- **Status:** **DONE**

### 13. Liquibase Migrations & DB Reliability
- **Requirement:** Every schema change is properly tracked and applied on boot.
- **Implementation:** `src/main/resources/db/changelog/` contains complete migration scripts.
- **API:** N/A (Startup process)
- **Test:** Server starts successfully over a completely empty MySQL DB without manual intervention.
- **Status:** **DONE**

### 14. Audit Logging & Security Event Logging
- **Requirement:** Comprehensive tracking of all domain mutations and security events with full context (`id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_value`, `new_value`, `ip_address`, `timestamp`). Strict data sanitization ensuring passwords, tokens, JWT secrets, and sensitive credentials are never stored in plain text (`[REDACTED]`). Administrative audit search with multi-dimensional filtering (`userId`, `action`, `entityType`, `entityId`, `startDate`, `endDate`), pagination, single-record lookup by ID, and entity audit trails. Dedicated security event monitoring for 7 core security event types (`SUCCESSFUL_LOGIN`, `FAILED_LOGIN`, `USER_LOGOUT`, `EXPIRED_TOKEN`, `INVALID_TOKEN`, `UNAUTHORIZED_ACCESS`, `PASSWORD_CHANGED`) plus auxiliary events (`INACTIVE_USER_TOKEN`, `TOKEN_REFRESHED`, `UNKNOWN_USER_TOKEN`). High-performance indexing on `audit_logs` (`timestamp`, `action`, `entity_type`, `entity_id`, `user_id`).
- **Implementation:** 
  - `AuditService`: core service handling `logAction()` and `logSecurityEvent()` with `Propagation.REQUIRES_NEW`, automated username and client IP extraction from request/security contexts, and regex-based sensitive credential redaction.
  - `SecurityEventType`: canonical constants defining all standard security event types and entity identifiers.
  - `SecurityEventListener`: Spring event listener capturing `AuthenticationSuccessEvent` (`SUCCESSFUL_LOGIN`), `AbstractAuthenticationFailureEvent` (`FAILED_LOGIN`), and `AuthorizationDeniedEvent` (`UNAUTHORIZED_ACCESS`).
  - `CustomAuthenticationEntryPoint` & `CustomAccessDeniedHandler`: capturing unauthenticated 401s and forbidden 403s as `UNAUTHORIZED_ACCESS` security events.
  - `JwtAuthenticationFilter`: intercepting token expiration (`EXPIRED_TOKEN`), malformed/tampered tokens (`INVALID_TOKEN`), disabled accounts (`INACTIVE_USER_TOKEN`), and non-existent users (`UNKNOWN_USER_TOKEN`).
  - `GlobalExceptionHandler`: intercepting refresh token exceptions (`EXPIRED_TOKEN`, `INVALID_TOKEN`) and method access denials (`UNAUTHORIZED_ACCESS`).
  - `AuthService`: event-driven logging for `USER_LOGOUT`, `TOKEN_REFRESHED`, and `PASSWORD_CHANGED`.
  - `AuditAspect`: AOP aspect intercepting methods annotated with `@AuditAction` across `LeaveRequestService`, `LeaveBalanceService`, `LeaveTypeService`, `EmployeeService`, and `DepartmentService`.
  - `AuditLogController`: administrative REST endpoints:
    - `GET /api/audit-logs`: general audit trail search with multi-field filtering and pagination.
    - `GET /api/audit-logs/security-events`: dedicated security events endpoint filtering on `entityType="SECURITY_EVENT"`, supporting `action`, `userId`, `startDate`, `endDate`, and pagination.
    - `GET /api/audit-logs/{id}`: single log lookup.
    - `GET /api/audit-logs/entity/{entityType}/{entityId}`: entity audit trail.
    All protected by `@PreAuthorize("hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('AUDIT_LOG_VIEW')")`.
  - Liquibase migration `016-add-audit-log-indexes.yaml`: dedicated performance indexes for query optimization.
- **API:** 
  - `GET /api/audit-logs?userId=...&action=...&entityType=...&entityId=...&startDate=...&endDate=...&page=0&size=20`
  - `GET /api/audit-logs/security-events?userId=...&action=...&startDate=...&endDate=...&page=0&size=20`
  - `GET /api/audit-logs/{id}`
  - `GET /api/audit-logs/entity/{entityType}/{entityId}`
- **Test:** 
  - `AuditServiceTest` (12 unit tests): action logging, security events, null tolerance, sensitive field redaction, context extraction, truncation of long values, specification filtering, and ID lookups.
  - `AuditAspectTest` (3 unit tests): DTO ID extraction, argument fallback, and exception safety.
  - `AuditLogControllerIntegrationTest` (15 integration tests): admin search, action filtering, entity type/ID filtering, date range filtering, RBAC protection (403 for employee, 401 unauthenticated), single-record retrieval, entity trail querying, department lifecycle auditing, login auditing, unauthorized access auditing, and data redaction verification.
  - `SecurityEventIntegrationTest` (13 integration tests): comprehensive end-to-end suite verifying all 7 security events (`SUCCESSFUL_LOGIN`, `FAILED_LOGIN`, `USER_LOGOUT`, `EXPIRED_TOKEN`, `INVALID_TOKEN`, `UNAUTHORIZED_ACCESS` 401/403, `PASSWORD_CHANGED`), token refresh events, strict password/token credential redaction in audit logs, dedicated `/api/audit-logs/security-events` endpoint functionality, admin filtering, and RBAC enforcement (403 employee, 401 unauthenticated).
- **Status:** **DONE**

### 15. User Login Tracking & History
- **Requirement:** Comprehensive tracking of user login activity and chronological login history across the system:
  - User-level login metrics on the `User` entity: `last_login_at` (timestamp of most recent successful login), `last_login_ip` (client IP address of most recent successful login), and `login_count` (lifetime count of successful logins).
  - Detailed chronological login attempt history (`login_history` table) capturing both successful and failed attempts: `id`, `user_id` (FK to `users`), `email` (login identifier used), `login_time`, `ip_address` (supporting proxies and IPv4/IPv6 via `X-Forwarded-For`), `user_agent` (browser/client device metadata), `status` (`SUCCESS` or `FAILED`), and `failure_reason`.
  - Strict security and credential sanitization: passwords and raw credentials submitted during failed login attempts must never be logged or persisted in `failure_reason` (only sanitized reasons such as `Bad credentials` or `User account is locked`).
  - Self-service endpoints for authenticated employees to inspect their own login history and summary metrics (`/api/login-history/me`, `/api/login-history/me/summary`, `/api/auth/login-history`).
  - Administrative endpoints for searching and auditing user login history across the organization with multi-dimensional filtering (`userId`, `email`, `status`, `startDate`, `endDate`), pagination, and user login summaries.
  - Performance indexing on `login_history` (`user_id`, `email`, `login_time`, `status`).
- **Implementation:** 
  - Database migration: `017-add-login-tracking.yaml` adding columns `last_login_at`, `last_login_ip`, `login_count` to `users`, creating `login_history` table with foreign key `fk_login_history_user`, and creating performance indexes `idx_login_history_user_id`, `idx_login_history_email`, `idx_login_history_time`, and `idx_login_history_status`. Registered in `db.changelog-master.yaml`.
  - Domain models: `User` entity updated with `lastLoginAt`, `lastLoginIp`, and `loginCount`. `LoginHistory` entity mapped to `login_history` table.
  - DTOs: `LoginHistoryResponseDTO`, `LoginSummaryDTO`, and updated `UserResponseDTO` exposing login tracking fields.
  - Repository: `LoginHistoryRepository` extending `JpaRepository` and `JpaSpecificationExecutor<LoginHistory>`, featuring custom query methods `findByUserIdOrderByLoginTimeDesc`, `findByEmailOrderByLoginTimeDesc`, and status count queries.
  - Service: `LoginTrackingService` managing:
    - `recordLoginSuccess(User, HttpServletRequest)`: updates `lastLoginAt`, `lastLoginIp`, increments `loginCount`, and persists `SUCCESS` login history.
    - `recordLoginFailure(String email, String failureReason, HttpServletRequest)`: `@Transactional(propagation = Propagation.REQUIRES_NEW)` ensures failed attempts persist even when authentication failure triggers a rollback, safely resolving the optional user without leaking passwords.
    - `getLoginHistoryForCurrentUser(UserDetails, Pageable)`, `getLoginSummaryForCurrentUser(UserDetails)`, `getLoginHistoryForUser(Long, Pageable)`, `getLoginSummaryForUser(Long)`, and `searchLoginHistory(...)` with specification-based filtering.
  - Security integration: `SecurityEventListener` hooked into Spring Security's `AuthenticationSuccessEvent` and `AbstractAuthenticationFailureEvent`, delegating to `@Lazy LoginTrackingService` with client IP and User-Agent extraction.
  - REST Controllers:
    - `LoginHistoryController`: `/api/login-history/me` and `/api/login-history/me/summary` for self-service employee access; `/api/login-history` and `/api/login-history/user/{userId}` for administrator search and user metrics (`hasAuthority('ADMIN_VIEW') or hasAuthority('ROLE_ADMIN') or hasAuthority('LOGIN_HISTORY_VIEW')`).
    - `AuthController`: convenient self-service endpoint `GET /api/auth/login-history` returning recent login history for the currently authenticated user.
  - Security Configuration: `SecurityConfig` rules explicitly protecting `/api/auth/login-history/**` as authenticated before permitAll `/api/auth/**`.
- **API:** 
  - `GET /api/login-history/me?page=0&size=20` (Authenticated)
  - `GET /api/login-history/me/summary` (Authenticated)
  - `GET /api/auth/login-history?page=0&size=20` (Authenticated)
  - `GET /api/login-history?userId=...&email=...&status=...&startDate=...&endDate=...&page=0&size=20` (Admin / Login History View)
  - `GET /api/login-history/user/{userId}?page=0&size=20` (Admin / Login History View)
  - `GET /api/login-history/user/{userId}/summary` (Admin / Login History View)
- **Test:** 
  - `LoginTrackingIntegrationTest` (10 integration tests): verifying successful login records with IP/User-Agent, user login count incrementing, failed login tracking with sanitized reasons and without credential leakage, self-service history and summary endpoints (`/api/login-history/me`, `/api/login-history/me/summary`, `/api/auth/login-history`), administrative multi-field filtering (`userId`, `email`, `status`), and RBAC enforcement (403 for employee on admin endpoints, 401 for unauthenticated requests).
- **Status:** **DONE**

### 16. Authorization Auditing
- **Requirement:** Enterprise-grade authorization auditing tracking authorization decisions (`GRANTED` and `DENIED`), business authorization checks, RBAC policy mutations, and security denials across the leave management system:
  - Dedicated storage table `authorization_audit_logs`: `id`, `user_id` (FK to `users`, nullable for anonymous/unauthenticated attempts), `email`, `action` (e.g. `LEAVE_APPROVAL_CHECK`, `EMPLOYEE_VIEW_CHECK`, `ROLE_PERMISSION_CHANGE`, `USER_ROLE_ASSIGNED`, `ENDPOINT_ACCESS`), `resource` (target entity/resource identifier), `permission_required` (expected authority or policy rule), `decision` (`GRANTED` or `DENIED`), `reason` (evaluation rationale), `ip_address` (client IP via direct or `X-Forwarded-For`), and `timestamp`.
  - Comprehensive auditing of business authorization checks in `SecurityAuthorizationService`:
    - `canApproveLeaveRequest`: audits granted/denied decisions with caller email, required `LEAVE_APPROVE` authority, and manager hierarchy verification.
    - `canRejectLeaveRequest`: audits granted/denied decisions with caller email and required `LEAVE_REJECT` authority.
    - `canCancelLeaveRequest`: audits granted/denied decisions with caller email and required `LEAVE_CANCEL` authority.
    - `canViewManagerTeam`: audits granted/denied decisions with caller email and required `LEAVE_VIEW_TEAM` / `EMPLOYEE_VIEW` authority.
    - `canManageLeaveBalance`: audits granted/denied decisions with caller email and required `LEAVE_BALANCE_MANAGE` authority.
    - `canViewEmployee`: audits granted/denied decisions with caller email and self/manager scope.
    - `canViewLeaveRequest`: audits granted/denied decisions with caller email and self/manager scope.
  - Interception and auditing of security denials:
    - `SecurityEventListener`: listens for Spring Security `AuthorizationDeniedEvent` to audit method and web security rejections.
    - `CustomAccessDeniedHandler`: audits 403 Forbidden endpoint denials with client IP and target resource.
    - `CustomAuthenticationEntryPoint`: audits 401 Unauthorized unauthenticated requests with client IP and target resource.
  - Auditing of RBAC policy mutations:
    - `RolePermissionService`: audits role permission replacements, additions, and removals (`ROLE_PERMISSIONS_REPLACED`, `ROLE_PERMISSION_ADDED`, `ROLE_PERMISSION_REMOVED`).
    - `EmployeeService`: audits user role modifications (`USER_ROLE_ASSIGNED`).
  - Self-service endpoints for authenticated employees to inspect their own authorization decision history (`/api/authorization-audits/me`).
  - Administrative search and metrics endpoints:
    - `GET /api/authorization-audits`: multi-criteria filtering (`userId`, `email`, `action`, `resource`, `decision`, `startDate`, `endDate`) with pagination.
    - `GET /api/authorization-audits/summary`: aggregated metrics (total evaluated, total granted, total denied, grant rate percentage).
    - `GET /api/authorization-audits/user/{userId}`: user-specific authorization audit trail.
    - `GET /api/authorization-audits/{id}`: single record retrieval.
    - `GET /api/audit-logs/authorization`: unified bridge endpoint alongside general and security audit logs.
  - Performance indexing on `authorization_audit_logs` (`user_id`, `email`, `timestamp`, `decision`, `action`).
- **Implementation:**
  - Database migration: `018-add-authorization-auditing.yaml` defining table `authorization_audit_logs` with foreign key `fk_authorization_audit_user` and indexes `idx_authz_audit_user_id`, `idx_authz_audit_email`, `idx_authz_audit_time`, `idx_authz_audit_decision`, and `idx_authz_audit_action`. Registered in `db.changelog-master.yaml`.
  - Domain models: `AuthorizationAuditLog` entity with mapped relationships and constants `DECISION_GRANTED` and `DECISION_DENIED`.
  - DTOs: `AuthorizationAuditResponseDTO` and `AuthorizationSummaryDTO`.
  - Repository: `AuthorizationAuditLogRepository` extending `JpaRepository` and `JpaSpecificationExecutor<AuthorizationAuditLog>`.
  - Service: `AuthorizationAuditService` implementing:
    - `recordDecision(...)`: `@Transactional(propagation = Propagation.REQUIRES_NEW)` ensuring audit records persist independently of business transaction rollbacks, with length truncation safeguards.
    - `recordGranted(...)` and `recordDenied(...)`: convenience evaluation recorders.
    - `searchAuthorizationAudits(...)`: dynamic JPA Specification filter supporting all filter criteria and pagination.
    - `getSummaryMetrics(...)`: aggregate decision counts and computed grant rate percentage.
    - `getAuditsForUser(...)`: user-scoped audit pagination.
  - Security configuration & listeners:
    - `SecurityConfig`: registered `SpringAuthorizationEventPublisher` bean enabling Spring Security 6/7 authorization event publication.
    - `SecurityEventListener`: hooked into `AuthorizationDeniedEvent` to record `DENIED` decisions with safe method reflection and resource formatting.
    - `CustomAccessDeniedHandler`: records 403 Forbidden denials.
    - `CustomAuthenticationEntryPoint`: records 401 Unauthorized denials.
  - REST Controllers:
    - `AuthorizationAuditController`: `/api/authorization-audits`, `/api/authorization-audits/summary`, `/api/authorization-audits/me`, `/api/authorization-audits/user/{userId}`, `/api/authorization-audits/{id}` protected by `ADMIN_SECURITY_VIEW` or `ROLE_ADMIN`.
    - `AuditLogController`: unified bridge endpoint `GET /api/audit-logs/authorization`.
- **API:**
  - `GET /api/authorization-audits?userId=...&email=...&action=...&decision=...&startDate=...&endDate=...&page=0&size=20` (Admin / Security View)
  - `GET /api/authorization-audits/summary` (Admin / Security View)
  - `GET /api/authorization-audits/{id}` (Admin / Security View)
  - `GET /api/authorization-audits/user/{userId}?page=0&size=20` (Admin / Security View)
  - `GET /api/authorization-audits/me?page=0&size=20` (Authenticated)
  - `GET /api/audit-logs/authorization?userId=...&decision=...&page=0&size=20` (Admin / Audit Log View)
- **Test:**
  - `AuthorizationAuditingIntegrationTest` (13 integration tests): verifying manager leave approval granted audit, unauthorized employee leave approval denied audit, unauthenticated 401 denied audit, employee accessing admin endpoint 403 denied audit, role permission addition and removal audits, employee role assignment audit, administrative multi-dimensional search filtering (`decision=DENIED`, `decision=GRANTED`), summary metrics calculation, single audit retrieval by ID, user-specific audits retrieval, self-service `/api/authorization-audits/me` endpoint, and unified bridge endpoint `/api/audit-logs/authorization`.
- **Status:** **DONE**

### 17. Sensitive-Data Protection
- **Requirement:** Comprehensive enterprise sensitive-data protection architecture safeguarding PII, financial compensation, authentication secrets, and credentials across storage, serialization, auditing, and data lifecycle management:
  - Multi-type data masking engine supporting:
    - `PHONE`: Preserves international country code and last 4 digits while masking middle digits (`+1 (***) ***-7890`, `******7890`).
    - `EMAIL`: Masks the middle of the username while preserving first/last character and full domain (`j****e@example.com`).
    - `SALARY`: Formats as redacted currency (`$***,***.00`).
    - `TOKEN`: Redacts all but first 4 and last 4 characters (`eyJh...8901`).
    - `PASSWORD` / `CREDENTIAL`: Complete redaction (`[PROTECTED]`).
    - `CREDIT_CARD`: Preserves first 4 and last 4 digits (`4111-****-****-1111`).
    - `NATIONAL_ID`: Preserves last 4 digits (`***-**-6789`).
    - `GENERIC`: Contextual partial masking preserving boundaries.
    - JSON & Key-Value sanitization for log/audit streams redacting sensitive fields (`password`, `token`, `secret`, `salary`, `phone`, `ssn`, `creditCard`, `cvv`, `apiKey`).
  - Cryptographic operations at rest and transit:
    - Authenticated symmetric encryption: AES-256-GCM (`AES/GCM/NoPadding`) with unique 12-byte cryptographically secure random IV per operation and 128-bit authentication tag to prevent tampering and ciphertext manipulation.
    - Cryptographic key management: Key derivation via SHA-256 from application encryption key (`app.security.encryption.key`).
    - Deterministic HMAC-SHA256 blind indexing (`generateHmacBlindIndex`) allowing exact-match searches over encrypted fields without decrypting records or exposing plaintext.
    - JPA Attribute Converter (`AesGcmEncryptionConverter`): Transparent database column-level encryption/decryption with graceful plaintext fallback for zero-downtime migrations.
  - Field-level contextual access control & Jackson serialization:
    - `@SensitiveData` declarative annotation with `@JacksonAnnotationsInside` and custom `SensitiveDataSerializer`.
    - Dynamic contextual evaluation based on Spring Security caller context (`ROLE_ADMIN`, `ROLE_HR`, or specific permissions like `EMPLOYEE_SALARY_VIEW`).
    - Non-privileged callers receive masked phone numbers and nullified or masked salary fields on employee profiles/DTOs (`EmployeeResponseDTO`).
  - Log & Audit Trail Protection:
    - `AuditService` regex scrubbing updated with sensitive patterns for salaries, phone numbers, SSNs, credit cards, CVVs, and API keys.
  - GDPR Compliance & Privacy Lifecycle:
    - Article 17 Right to Erasure / Anonymization: `DataAnonymizationService.anonymizeEmployee` wipes PII (email converted to anonymized hash, names scrubbed, phone cleared, salary zeroed, user deactivated) while preserving referential integrity for organizational audit compliance.
    - Article 20 Right to Data Portability: `DataAnonymizationService.exportPersonalData` aggregates and packages personal identity data, department information, role assignments, leave balance quotas, and historical leave requests into an exportable JSON payload (`PersonalDataExportDTO`).
  - REST Endpoints:
    - `POST /api/sensitive-data/mask`: On-demand masking utility endpoint for text, emails, phones, and tokens.
    - `POST /api/sensitive-data/encrypt`: Admin cryptographic encryption endpoint.
    - `POST /api/sensitive-data/decrypt`: Admin cryptographic decryption endpoint with integrity check.
    - `POST /api/sensitive-data/anonymize/{employeeId}`: Admin GDPR right to erasure endpoint.
    - `GET /api/sensitive-data/me/export`: Self-service personal data portability export for authenticated users.
    - `GET /api/sensitive-data/export/{employeeId}`: Administrative personal data export for authorized HR/admins.
- **Implementation:**
  - Security package: `com.leave_management_system.leave_management_system.security.sensitive`:
    - `SensitiveType`: Enum specifying sensitive categories (`PHONE`, `EMAIL`, `SALARY`, `TOKEN`, `PASSWORD`, `CREDENTIAL`, `CREDIT_CARD`, `NATIONAL_ID`, `GENERIC`).
    - `SensitiveData`: Meta-annotation binding `SensitiveDataSerializer`.
    - `DataMaskingService`: Core masking algorithms, JSON string redactor, and Map sanitizer.
    - `CryptoService`: AES-256-GCM authenticated encryption/decryption and HMAC-SHA256 blind indexing.
    - `AesGcmEncryptionConverter`: JPA AttributeConverter for database entity columns.
    - `SensitiveDataSerializer`: Jackson ContextualSerializer enforcing field-level role/permission/ownership authorization checks.
    - `DataAnonymizationService`: GDPR Article 17 erasure and Article 20 data portability export engine.
  - DTOs:
    - `MaskRequestDTO`, `MaskResponseDTO`
    - `CryptoRequestDTO`, `CryptoResponseDTO`
    - `PersonalDataExportDTO`, `AnonymizeResponseDTO`
  - Controller:
    - `SensitiveDataController`: Exposes endpoints for masking, encryption/decryption, GDPR erasure, and personal data export with `@PreAuthorize` security checks.
  - Audit & DTO Integration:
    - `AuditService`: Expanded sensitive regex to redact `salary`, `phone`, `ssn`, `creditCard`, `cvv`, `apiKey`.
    - `EmployeeResponseDTO`: Added `@SensitiveData(type = SensitiveType.SALARY, privilegeRequired = "ROLE_ADMIN")` and `@SensitiveData(type = SensitiveType.PHONE)`.
- **API:**
  - `POST /api/sensitive-data/mask` (Authenticated)
  - `POST /api/sensitive-data/encrypt` (Admin / Security View)
  - `POST /api/sensitive-data/decrypt` (Admin / Security View)
  - `POST /api/sensitive-data/anonymize/{employeeId}` (Admin / User Manage)
  - `GET /api/sensitive-data/me/export` (Authenticated)
  - `GET /api/sensitive-data/export/{employeeId}` (Admin / Employee View)
- **Test:**
  - `SensitiveDataProtectionIntegrationTest` (13 integration tests): Phone and email masking API verification; AES-256-GCM encryption/decryption roundtrip; AES-256-GCM ciphertext tampering detection; deterministic HMAC-SHA256 blind indexing; employee self-service personal data export (GDPR Art. 20); admin personal data export for target employee; non-admin forbidden from exporting other employees' personal data (403); admin employee anonymization (GDPR Art. 17) verifying PII scrub and account deactivation; non-admin forbidden from anonymizing employees (403); JPA column converter transparent encryption and plaintext fallback; audit log sanitization redacting salary, phone, and tokens; unauthenticated request rejection (401).
- **Status:** **DONE**

### 18. Leave & Balance Transaction Review Engine
- **Requirement:**
  - Automated, risk-scored transaction monitoring and review system for all sensitive leave and balance operations across the organization.
  - Automated heuristic risk evaluation and classification:
    - Standard routine leave requests: `LOW` risk (score 10), automatically approved/reviewed.
    - Extended leave requests: duration > 14 days flagged as `MEDIUM` risk (score 45), duration > 30 days flagged as `HIGH` risk (score 75).
    - Retroactive leave submissions: start date before creation date flagged as `HIGH` risk (score 80).
    - Post-approval leave cancellations: cancellation of previously approved requests flagged as `MEDIUM` risk (score 50).
    - Manual leave balance adjustments: admin balance overrides with absolute delta >= 10 days flagged as `HIGH` risk (score 75), delta >= 20 days flagged as `CRITICAL` risk (score 95).
    - Generic administrative transactions: role changes, department transfers.
  - Multi-state transaction review lifecycle:
    - States: `PENDING_REVIEW`, `UNDER_REVIEW`, `REVIEWED`, `FLAGGED`, `RESOLVED`.
    - Risk levels: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` with numerical risk score (0-100).
    - Reviewer assignment (`assignReviewer`) transitioning review to `UNDER_REVIEW`.
    - Review decision submission (`submitReview`) transitioning review to `REVIEWED`.
    - Suspicious transaction flagging (`flagTransaction`) with escalation reason transitioning review to `FLAGGED`.
    - Disposition resolution (`resolveReview`) with resolution notes transitioning review to `RESOLVED`.
  - Dynamic Search, Filtering & Analytics:
    - Multi-criteria specification queries filtering by `transactionType`, `reviewStatus`, `riskLevel`, `employeeId`, `actorId`, `reviewerId`, `minRiskScore`, and date ranges (`startDate`, `endDate`).
    - Aggregated executive metrics summary endpoint (`/api/transaction-reviews/summary`) reporting total transactions, status breakdowns, risk level distributions, pending high-risk queue size, and review completion rates.
    - Role-based contextual access: Self-service endpoint (`/me`) for employees, managed employee endpoint (`/employee/{id}`) for managers/admins, full review control for Admin and Audit viewers.
    - Unified audit log bridging: Cross-system lookup endpoint under `/api/audit-logs/transaction-reviews`.
- **Implementation:**
  - Liquibase Migration:
    - `019-add-transaction-review.yaml`: Creates `transaction_reviews` table with indexed foreign keys (`employee_id`, `actor_id`, `reviewer_id`) with cascading delete rules, risk attributes, lifecycle status, JSON details, and search indexes.
  - Domain Model & Repositories:
    - Enums: `TransactionType`, `ReviewStatus`, `RiskLevel`.
    - Entity: `TransactionReview` tracking transaction references, actor, target employee, risk score/reason, review status, reviewer assignment, review notes, resolution disposition, and contextual JSON details.
    - Repository: `TransactionReviewRepository` with `JpaSpecificationExecutor` and aggregate metric count queries.
  - DTOs:
    - `TransactionReviewResponseDTO`, `TransactionReviewSummaryDTO`, `ReviewDecisionDTO`, `FlagTransactionDTO`, `ResolveReviewDTO`, `AssignReviewerDTO`.
  - Service Layer:
    - `TransactionReviewService`: Automated heuristic risk evaluation, lifecycle state machines, criteria-based filtering, and KPI calculation.
    - `LeaveRequestService`: Integrated with `TransactionReviewService` on creation, approval, rejection, and cancellation.
    - `LeaveBalanceService`: Integrated with `TransactionReviewService` on quota creation and manual balance adjustments.
  - Security & Controllers:
    - `SecurityAuthorizationService.canViewTransactionReview`: Role, hierarchy, and ownership validation.
    - `TransactionReviewController`: REST endpoints under `/api/transaction-reviews`.
    - `AuditLogController`: Unified bridging endpoint `GET /api/audit-logs/transaction-reviews`.
- **API:**
  - `GET /api/transaction-reviews` (Admin / Audit Log View)
  - `GET /api/transaction-reviews/summary` (Admin / Audit Log View)
  - `GET /api/transaction-reviews/me` (Authenticated Employee)
  - `GET /api/transaction-reviews/employee/{employeeId}` (Manager / Admin)
  - `GET /api/transaction-reviews/{id}` (Contextual Authorization)
  - `POST /api/transaction-reviews/{id}/assign` (Admin / Leave Approve)
  - `POST /api/transaction-reviews/{id}/review` (Admin / Leave Approve)
  - `POST /api/transaction-reviews/{id}/flag` (Admin / Leave Approve)
  - `POST /api/transaction-reviews/{id}/resolve` (Admin / Leave Approve)
  - `GET /api/audit-logs/transaction-reviews` (Admin / Audit Log View)
- **Test:**
  - `TransactionReviewIntegrationTest` (14 integration tests):
    - Routine leave request creates low-risk reviewed transaction review.
    - Extended leave request (> 14 days) triggers MEDIUM risk and PENDING_REVIEW status.
    - Retroactive leave request creates HIGH risk review flagged for verification.
    - Leave cancellation post-approval triggers MEDIUM risk PENDING_REVIEW.
    - Manual balance adjustment with significant delta triggers HIGH risk FLAGGED review.
    - Multi-criteria specification filtering and pagination.
    - Reviewer assignment transitioning review to UNDER_REVIEW.
    - Review submission with notes transitioning to REVIEWED.
    - Flagging suspicious transaction with escalation note transitioning to FLAGGED.
    - Resolving flagged review with disposition note transitioning to RESOLVED.
    - Executive summary KPI metrics computation.
    - Employee self-service `/me` endpoint returns owned transaction reviews.
    - Contextual authorization check preventing unauthorized employee access (403).
    - Unified audit log bridging endpoint queries transaction reviews.
- **Status:** **DONE**

### 19. Security Event Management & Automated Threat Detection Engine
- **Requirement:**
  - Dedicated first-class security event management and automated threat detection engine.
  - Track security events: `SUCCESSFUL_LOGIN`, `FAILED_LOGIN`, `USER_LOGOUT`, `EXPIRED_TOKEN`, `INVALID_TOKEN`, `UNAUTHORIZED_ACCESS`, `PASSWORD_CHANGED`, `INACTIVE_USER_TOKEN`, `TOKEN_REFRESHED`, `UNKNOWN_USER_TOKEN`, `BRUTE_FORCE_ATTEMPT`, `REPEATED_UNAUTHORIZED_ACCESS`, `SUSPICIOUS_TOKEN_ACTIVITY`, `PRIVILEGE_ESCALATION`, `SUSPICIOUS_IP_ACTIVITY`.
  - Severity classification: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` with numerical threat scoring (0-100).
  - Incident lifecycle tracking: `OPEN`, `INVESTIGATING`, `RESOLVED`, `FALSE_POSITIVE` with resolution notes and reviewer timestamps.
  - Automated threat detection heuristics:
    - Brute-force detection: $\ge$ 5 failed logins within 15 minutes triggers `BRUTE_FORCE_ATTEMPT` with `CRITICAL` severity (score 95).
    - Repeated unauthorized access: $\ge$ 3 unauthorized requests within 10 minutes triggers `REPEATED_UNAUTHORIZED_ACCESS` with `HIGH` severity (score 85).
    - Suspicious token failures: $\ge$ 4 invalid/expired token requests within 10 minutes triggers `SUSPICIOUS_TOKEN_ACTIVITY` with `HIGH` severity (score 80).
  - Multi-criteria specification filtering: search by `eventType`, `severity`, `status`, `userId`, `email`, `ipAddress`, `minThreatScore`, `startDate`, `endDate`, and pagination.
  - Executive security posture summary endpoint (`/api/security-events/summary`) reporting total events, open/investigating/resolved/false positive counts, severity breakdowns, pending high-risk queue size, and resolution rate %.
  - Strict credential sanitization: passwords, tokens, JWTs, and sensitive keys are redacted with `[REDACTED]`.
  - Seamless bridge with unified audit trail: `AuditService.logSecurityEvent` feeds both `audit_logs` and `security_events`.
- **Implementation:**
  - Liquibase Migration:
    - `020-add-security-events.yaml`: Schema migration creating `security_events` table with indexed foreign keys (`user_id`, `resolved_by_id`) with cascading delete rules, threat scores, lifecycle status, resolution details, and search indexes.
  - Domain Model & Repositories:
    - Enums: `SecurityEventSeverity`, `SecurityEventStatus`.
    - Constants: Extended `SecurityEventType` with threat detection event types.
    - Entity: `SecurityEvent` tracking event type, severity, status, user, email, IP, user-agent, resource, threat score, threat reason, resolution notes, resolver, resolved timestamp, and details.
    - Repository: `SecurityEventRepository` with `JpaSpecificationExecutor` and threat detection window queries.
  - DTOs:
    - `SecurityEventResponseDTO`, `SecurityEventSummaryDTO`, `UpdateSecurityEventStatusDTO`.
  - Service Layer:
    - `SecurityEventManagementService`: Event recording, baseline severity scoring, automated threat heuristics, multi-criteria filtering, lifecycle state transitions, and executive KPI calculation.
    - `AuditService`: Connected to `SecurityEventManagementService` on `logSecurityEvent`.
  - Security & Controllers:
    - `SecurityEventController`: REST endpoints under `/api/security-events`.
- **API:**
  - `GET /api/security-events` (Admin / Audit Log View / HR)
  - `GET /api/security-events/summary` (Admin / Audit Log View / HR)
  - `GET /api/security-events/{id}` (Admin / Audit Log View / HR)
  - `PATCH /api/security-events/{id}/status` (Admin / Audit Log View)
  - `POST /api/security-events/{id}/investigate` (Admin / Audit Log View)
  - `POST /api/security-events/{id}/resolve` (Admin / Audit Log View)
  - `POST /api/security-events/{id}/false-positive` (Admin / Audit Log View)
- **Test:**
  - `SecurityEventManagementIntegrationTest` (12 integration tests):
    - Routine login creates LOW severity, auto-resolved security event.
    - Failed login creates MEDIUM severity, OPEN security event with threat score 40.
    - Automated brute-force detection triggers CRITICAL alert after 5 failed logins within 15 minutes.
    - Repeated unauthorized access triggers HIGH threat alert after 3 unauthorized attempts.
    - Suspicious token activity triggers HIGH threat alert after 4 token validation failures.
    - Multi-criteria specification filtering by event type, severity, and minimum threat score.
    - Get security event by ID (success and 404).
    - Status lifecycle update via PATCH.
    - Convenience status transition endpoints (investigate, resolve, false positive) with resolution notes and resolver metadata.
    - Executive security posture summary KPI metrics calculation.
    - Credential sanitization verifying passwords and tokens in payload details are redacted.
    - RBAC enforcement: non-admin employee forbidden (403), unauthenticated rejected (401).
- **Status:** **DONE**

---

### 24. Database Concurrency Locking & User Account Security Lockout
- **Requirement:**
  - **Database Concurrency Locking:** Prevent race conditions, double approval, double cancellation/refund, and dirty balance updates under high-concurrency requests through optimistic and pessimistic locking.
  - **User Account Lockout:** Protect against brute-force password guessing and credential stuffing attacks by locking accounts after repeated consecutive failed authentication attempts, with automated duration-based expiry/unlocking and administrative unlock/lock controls.
- **Implementation:**
  - **Concurrency Control:**
    - Optimistic Locking with `@Version` column on `LeaveRequest` and `LeaveBalance`.
    - Pessimistic Write Locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) in `LeaveRequestRepository` (`findByIdWithPessimisticLock`) and `LeaveBalanceRepository` (`findByIdWithPessimisticLock`, `findByEmployeeAndLeaveTypeAndYearWithPessimisticLock`).
    - Serialized lifecycle modifications in `LeaveRequestService` (`approveLeaveRequest`, `rejectLeaveRequest`, `cancelLeaveRequest`) and `LeaveBalanceService` (`updateLeaveBalance`, `deleteLeaveBalance`).
    - Global exception translation for JPA and Spring concurrency exceptions to HTTP 409 Conflict in `GlobalExceptionHandler`.
  - **Account Security Lockout:**
    - Schema migration `022-add-user-account-locking.yaml` adding `account_non_locked`, `failed_login_attempts`, and `lock_expires_at` to `users` table, and seeding `USER_LOCK_MANAGE` permission to `ADMIN` and `SECURITY_OFFICER` roles.
    - Configured thresholds in `application.properties`: `app.security.lockout.max-failed-attempts=5` and `app.security.lockout.duration-minutes=15`.
    - Automated lock enforcement in `LoginTrackingService` with `ACCOUNT_LOCKED` security event audit.
    - Automated duration-based unlocking in `CustomUserDetailsService` upon credential verification.
    - Administrative self-service REST endpoints in `UserController` and `UserService` (`PUT /api/users/{id}/unlock`, `PUT /api/users/{id}/lock`, `GET /api/users?locked=true`).
    - Spring Security `LockedException` mapping to HTTP 401 Unauthorized with "Account Locked" error details in `GlobalExceptionHandler`.
- **API:**
  - `PUT /api/users/{id}/unlock` (Admin / Security Officer with `USER_LOCK_MANAGE`)
  - `PUT /api/users/{id}/lock` (Admin / Security Officer with `USER_LOCK_MANAGE`)
  - `GET /api/users?locked=true` (Admin with `USER_LOCK_MANAGE`)
  - `GET /api/users/{id}` (Admin / Self with `USER_LOCK_MANAGE` or employee owner)
- **Test:**
  - `LeaveRequestServiceConcurrencyIntegrationTest` (5 tests):
    - `testConcurrentLeaveApprovalsThrowsOptimisticLockException`: Parallel approval attempts serialize; exactly one succeeds and the other fails on lock conflict.
    - `testConcurrentLeaveApplicationsThrowsOptimisticLockException`: Overlapping parallel leave creation requests are serialized and rejected.
    - `testConcurrentLeaveCancellationsDoesNotDoubleRefund`: Parallel cancellations serialize without double-refunding annual leave days.
    - `testPessimisticLockDirectRepositoryQueries`: Direct repository query execution with pessimistic write locks.
    - `testConcurrentBalanceUpdatesSerializedWithPessimisticLock`: Parallel balance modifications are safely serialized.
  - `ConcurrentLeaveApprovalIntegrationTest` (7 HTTP integration tests):
    - `concurrentSimultaneousApprovals_sameRequest_exactlyOneSucceeds_andBalanceDeductedOnce`: Two concurrent HTTP threads invoke `PUT /api/leave-requests/{id}/approve`; exactly one receives HTTP 200 OK while the other receives HTTP 400/409, and leave balance is deducted only once.
    - `concurrentSimultaneousApprovals_fiveThreads_exactlyOneSucceeds_othersFailCleanly`: 5 concurrent HTTP threads hit the approval endpoint simultaneously; exactly 1 succeeds and 4 fail cleanly.
    - `concurrentSimultaneousApprovals_differentHttpMethods_putAndPatch_serializeCleanly`: Concurrent PUT and PATCH approvals serialize cleanly without double balance deduction.
    - `concurrentApprovalAndCancellation_onSameRequest_maintainsBalanceIntegrity`: Concurrent approval and cancellation preserve balance integrity and invariant total.
    - `concurrentApprovalAndRejection_onSameRequest_exactlyOneWins`: Concurrent approval and rejection resolve exclusively without inconsistent state.
    - `concurrentApprovals_overlappingRequestsForSameEmployee_onlyOneApproved`: Two concurrent approval requests on overlapping date ranges for the same employee serialize; only 1 is approved and the other fails with overlap error.
    - `concurrentApprovals_competingForInsufficientRemainingBalance_onlyOneFits`: Two non-overlapping requests competing for limited remaining balance serialize; only 1 succeeds and the other fails with Insufficient Leave Balance without dipping below zero.
  - `ConcurrentLeaveApplicationIntegrationTest` (5 HTTP integration tests):
    - `concurrentSimultaneousApplications_sameEmployee_identicalDates_exactlyOneSucceeds_otherFailsWithOverlap`: Simultaneous duplicate submissions (e.g. double-click) serialize on employee pessimistic lock; exactly 1 succeeds and the duplicate fails with overlap (HTTP 400/409).
    - `concurrentSimultaneousApplications_sameEmployee_differentLeaveTypes_overlappingDates_exactlyOneSucceeds`: Simultaneous requests on overlapping dates across different leave types (Annual vs Sick) serialize on employee lock, preventing bypass of overlap validation.
    - `concurrentSimultaneousApplications_sameEmployee_nonOverlappingDates_bothSucceed`: Simultaneous non-overlapping applications from the same employee both succeed cleanly.
    - `concurrentSimultaneousApplications_differentEmployees_sameDates_bothSucceedInParallel`: Simultaneous applications from distinct employees lock independent rows and execute with full parallel throughput.
    - `concurrentSimultaneousApplications_fiveThreads_sameDates_exactlyOneSucceeds`: 5 parallel threads submitting the same leave request serialize cleanly; exactly 1 is persisted.
  - `AccountLockoutIntegrationTest` (8 tests):
    - Consecutive failed logins trigger account lockout and record `ACCOUNT_LOCKED` security event.
    - Login attempts on a locked account return HTTP 401 with "Account Locked".
    - Successful login resets failed attempts counter to 0.
    - Expired lockout automatically unlocks account on subsequent login.
    - Admin with `USER_LOCK_MANAGE` can manually unlock an account.
    - Admin with `USER_LOCK_MANAGE` can administratively lock an account.
    - Non-privileged employee is forbidden (HTTP 403) from manual lock/unlock.
    - Admin can query users filtered by locked status.
- **Status:** **DONE**

### 27. Dual Parallel Concurrent Approval Workflow (Manager + HR)
- **Requirement:** Support concurrent parallel multi-approver workflow for leave requests requiring dual authorization (Manager + HR). Both reviewers can approve independently in any sequence. The request transitions to `PARTIALLY_APPROVED` upon the first sign-off without deducting balance; only after both reviewers sign off does the request transition to `APPROVED` and deduct balance from annual quota. Support administrative super-approval override, sign-off auditing, rejection/cancellation during partial approval, overlap detection, and retry protection under parallel race conditions.
- **Implementation:**
  - Liquibase migration `023-add-multi-approver-signoff.yaml` adding `requires_dual_approval`, `manager_approval_status`, `manager_approved_by`, `manager_approved_at`, `hr_approval_status`, `hr_approved_by`, `hr_approved_at` to `leave_requests`, and creating `leave_request_signoffs` table with foreign key cascade.
  - Domain entities: `LeaveStatus.PARTIALLY_APPROVED`, `LeaveRequestSignoff`, and one-to-many relationship in `LeaveRequest`.
  - Repositories: `LeaveRequestSignoffRepository` with `findByLeaveRequestId` and `findByLeaveRequestIdAndApproverRole`.
  - DTOs: `LeaveSignoffResponseDTO`, `LeaveRequestDTO` with `requiresDualApproval`, `LeaveResponseDTO` with dual sign-off metadata and list of sign-offs.
  - Service: `LeaveRequestService.approveLeaveRequest()` handles parallel sign-offs for Manager and HR independently, tracks role-specific status, transitions to `PARTIALLY_APPROVED` on first sign-off (without balance deduction), and calls `finalizeFullApproval()` on second sign-off (or on Admin direct approval) to atomically lock row/employee/balance and deduct days. Rejections and cancellations are supported in `PARTIALLY_APPROVED` state.
  - Controller: `LeaveRequestController.approveLeaveRequest()` equipped with exponential backoff retry on concurrency lock collisions. Endpoint `GET /api/leave-requests/{id}/signoffs` exposes full sign-off audit trail.
- **API:**
  - `PUT/PATCH /api/leave-requests/{id}/approve` (Manager, HR, Admin)
  - `PUT/PATCH /api/leave-requests/{id}/reject` (Manager, HR, Admin)
  - `PUT/PATCH /api/leave-requests/{id}/cancel` (Employee, Admin, HR)
  - `GET /api/leave-requests/{id}/signoffs` (Manager, HR, Admin, applicant)
  - `POST /api/leave-requests` (with `requiresDualApproval: true`)
- **Test:**
  - `DualConcurrentApprovalIntegrationTest` (8 tests):
    - `testDualApproval_managerThenHr_transitionsToApprovedAndDeductsBalance`: Manager approves first (status `PARTIALLY_APPROVED`, balance untouched, 1 signoff recorded) -> HR approves second (status `APPROVED`, balance deducted by 3 days, 2 signoffs recorded).
    - `testDualApproval_hrThenManager_transitionsToApprovedAndDeductsBalance`: HR approves first (status `PARTIALLY_APPROVED`, balance untouched) -> Manager approves second (status `APPROVED`, balance deducted).
    - `testDualApproval_adminDirectOverride_immediatelyApprovedAndDeducted`: Admin approves directly, bypassing requirement for both individual approvals, transitioning immediately to `APPROVED` and deducting balance.
    - `testDualApproval_concurrentSimultaneousApprovals_bothThreadsSucceed_statusApproved_balanceDeductedOnce`: Manager and HR approve at the exact same millisecond in parallel threads; both receive HTTP 200 OK, request reaches `APPROVED`, and balance is deducted strictly once.
    - `testDualApproval_duplicateSignoff_sameRole_returns400BadRequest`: Attempting to sign off twice as Manager returns HTTP 400 Bad Request with descriptive error.
    - `testDualApproval_rejectAfterManagerPartialApproval_rejectsAndLeavesBalanceUntouched`: Rejection after partial approval transitions request to `REJECTED` and preserves balance.
    - `testDualApproval_cancelAfterPartialApproval_cancelsCleanlyWithoutModifyingBalance`: Employee can cancel partially approved request without balance penalty.
    - `testDualApproval_overlapDetection_blocksOverlappingRequestWhenPartiallyApproved`: Active partially approved requests block subsequent overlapping applications.
- **Status:** **DONE**







