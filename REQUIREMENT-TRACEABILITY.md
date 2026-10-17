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
