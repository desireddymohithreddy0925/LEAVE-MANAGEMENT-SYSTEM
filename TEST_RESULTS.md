# Test Results

## Leave Cancellation Architecture & Security Lifecycle Implementation
- **Status:** Complete
- **Timestamp:** 2026-10-18
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 277 tests passed (0 failures, 0 errors, 0 skipped), including 6 newly added tests across `LeaveRequestServiceTest` (+3 new) and `LeaveRequestControllerIntegrationTest` (+3 new).
- **Cancellation Capabilities Implemented & Verified:**
  - **State Machine Transitions & Lifecycle Guards:**
    - Cancellation is allowed from `PENDING` and `APPROVED` states only.
    - Attempting to cancel a `REJECTED` leave returns `400 Bad Request` ("Cannot cancel a rejected leave request").
    - Attempting to cancel an already-`CANCELLED` leave returns `400 Bad Request` ("Leave request is already cancelled").
  - **Balance Restoration for Approved Leaves:**
    - When cancelling an `APPROVED` leave, `available` balance is incremented and `used` is decremented atomically using `findByEmployeeAndLeaveTypeAndYearWithLock` (`OPTIMISTIC_FORCE_INCREMENT`).
    - `used` is clamped to `Math.max(0, currentUsed - days)` to handle null or pre-existing zero values safely.
    - When cancelling a `PENDING` leave, balance is **not touched** (no balance was ever deducted).
  - **Single-Calendar-Year Balance Isolation:**
    - Restoration uses the leave's `startDate.getYear()` to target the correct annual leave balance; cross-year cancellations are rejected (`400 Bad Request`).
  - **Double-Cancellation Protection:**
    - Attempting to cancel an already-cancelled leave request immediately returns `400 Bad Request`.
  - **Security & Authorization (RBAC & Hierarchy Scoping):**
    - `@PreAuthorize("@securityService.canCancelLeaveRequest(authentication, #id)")` ensures:
      - Global Admin and HR can cancel any leave request.
      - Regular employees with `LEAVE_CANCEL` authority can only cancel their **own** leave requests (`isSelf` check).
      - Attempts by unrelated employees (even with `LEAVE_CANCEL`) to cancel another employee's leave return `403 Forbidden`.
  - **Flexible Multi-Method HTTP Support:**
    - `PUT`, `PATCH`, and `POST` all supported on `/api/leave-requests/{id}/cancel` for Postman and automation script compatibility.
  - **Audit Logging:**
    - Emits `@AuditAction(action = "LEAVE_CANCELLED", entityType = "LeaveRequest")` for full enterprise compliance.

## Leave Rejection Architecture & Security Lifecycle Implementation
- **Status:** Complete
- **Timestamp:** 2026-10-17
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 271 tests passed (0 failures, 0 errors, 0 skipped), including 16 newly added tests across `LeaveRequestServiceTest` (46 tests, +4 new), `LeaveRequestControllerIntegrationTest` (38 tests, +10 new), and `ValidationIntegrationTest` (20 tests, +2 new).

- **Rejection Capabilities Implemented & Verified:**
  - **State Machine Transitions & Lifecycle Guards:**
    - Strict transition from `PENDING` to `REJECTED`.
    - Prohibits rejecting non-pending requests: attempting to reject requests that are already `APPROVED`, `REJECTED`, or `CANCELLED` is blocked with `IllegalArgumentException` and HTTP `400 Bad Request` ("Only pending requests can be rejected").
    - Prevents double rejection.
  - **Mandatory Rejection Reason Validation:**
    - Mandatory, non-blank reason parameter enforced at service, controller, and DTO levels.
    - Missing, null, or whitespace-only reasons return HTTP `400 Bad Request`.
    - Reasons are automatically trimmed of surrounding whitespace.
    - Character length capped at 500 characters (`@Size(max = 500)` in `RejectRequestDTO` and validation in `LeaveRequestService`). Reasons exceeding 500 characters return HTTP `400 Bad Request`.
    - Liquibase migration `015-modify-rejection-reason-length.yaml` expanded the DB column `rejection_reason` to `varchar(500)`.
  - **Flexible Multi-Method and Payload Support:**
    - Supports `PUT`, `PATCH`, and `POST` on `/api/leave-requests/{id}/reject`.
    - Supports reason delivery via query parameter (`?reason=...`) or structured JSON body (`{"reason": "..."}`).
  - **Balance Preservation:**
    - Verified that rejecting a pending leave request leaves employee leave balances untouched (`available` and `used` days remain unchanged).
  - **Overlap Unblocking:**
    - Verified that once a leave request is rejected, the dates are freed up and the employee can immediately reapply for leave covering the same dates without overlap conflicts.
  - **Security & Authorization (RBAC & Hierarchy Scoping):**
    - `@PreAuthorize("@securityService.canRejectLeaveRequest(authentication, #id)")` ensures only Global Admin/HR or the employee's direct/department/hierarchy manager can reject.
    - Blocks regular employees without `LEAVE_REJECT` permission (HTTP `403 Forbidden`).
    - Blocks managers from self-rejecting their own leave requests (HTTP `403 Forbidden`).
    - Blocks cross-team / cross-department managers without reporting authority (HTTP `403 Forbidden`).
    - Returns HTTP `404 Not Found` for non-existent leave requests.
  - **Audit Logging:**
    - Emits `@AuditAction(action = "LEAVE_REJECTED", entityType = "LeaveRequest")` for full enterprise compliance.

## Leave Approval Architecture & Security Lifecycle Implementation
- **Status:** Complete
- **Timestamp:** 2026-10-17
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 255 tests passed (0 failures, 0 errors, 0 skipped), including 12 newly added tests across `LeaveRequestServiceTest` (42 tests, +5 new) and `LeaveRequestControllerIntegrationTest` (28 tests, +7 new).
- **Approval Capabilities Implemented & Verified:**
  - **State Machine Transition & Integrity:**
    - Strict transition from `PENDING` to `APPROVED`.
    - Prohibits approving non-pending requests: attempting to approve a request that is already `APPROVED`, `REJECTED`, or `CANCELLED` is blocked with `IllegalArgumentException` and HTTP `400 Bad Request`.
  - **Approving Overlapping Approved Leaves Prevention:**
    - Pre-approval check using `leaveRequestRepository.hasOverlappingLeaveExcludingId(employeeId, startDate, endDate, id, List.of(LeaveStatus.APPROVED))`.
    - Prevents approving a request if the employee already has an `APPROVED` leave request overlapping those dates (blocking concurrent pending approvals from corrupting calendar allocations).
  - **Single-Calendar-Year Balance Isolation & Deduction:**
    - Locks applicable year balance with optimistic force increment (`findByEmployeeAndLeaveTypeAndYearWithLock`).
    - Verifies sufficient available balance (`available >= workingDays`).
    - Deducts `available` balance and increments `used` balance in the same atomic transaction.
  - **Dual HTTP Method Support (`PUT` and `PATCH`):**
    - Both `PUT /api/leave-requests/{id}/approve` and `PATCH /api/leave-requests/{id}/approve` supported and verified.
  - **Strict Security & RBAC Scoping:**
    - `@PreAuthorize("@securityService.canApproveLeaveRequest(authentication, #id)")` ensures only Global Admin/HR or the employee's direct/department/hierarchy manager can approve.
    - Rejects unauthorized users or regular employees without `LEAVE_APPROVE` with HTTP `403 Forbidden`.
    - Handles non-existent leave requests cleanly with HTTP `404 Not Found`.
  - **Audit Logging:**
    - Emits `@AuditAction(action = "LEAVE_APPROVED", entityType = "LeaveRequest")` for full compliance auditing.

## Leave Overlap Detection Architecture & Pre-Flight API
- **Status:** Complete
- **Timestamp:** 2026-10-16
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 243 tests passed (0 failures, 0 errors, 0 skipped), including 12 newly added tests across `LeaveRequestRepositoryTest` (5 tests), `LeaveRequestServiceTest` (37 tests), and `LeaveRequestControllerIntegrationTest` (21 tests).
- **Overlap Capabilities Implemented & Verified:**
  - **Comprehensive Interval Overlap Mathematical Detection (`S1 <= E2 && E1 >= S2`):**
    - Verifies exact match, start overlap, end overlap, interior overlap, and enclosing overlap.
    - Verified boundary touching (single day at boundary: `E1 == S2` or `S1 == E2` detected as overlap).
    - Verified adjacent dates (immediately prior day or subsequent day: `E1 = S2 - 1` or `S1 = E2 + 1`) properly recognized as non-overlapping.
    - Verified cross-leave-type overlap detection: an employee cannot apply for a different leave type (e.g., Sick Leave) on dates where an existing leave (e.g., Annual Leave) is `PENDING` or `APPROVED`.
    - Verified multi-employee isolation: distinct employees can take leave on identical dates without false-positive overlap conflicts.
    - Verified state filtering: `REJECTED` and `CANCELLED` leave requests are excluded from overlap checks.
  - **Exclusion Queries & ID Filtering:**
    - Implemented `hasOverlappingLeaveExcludingId` and `findOverlappingLeavesExcludingId` to exclude specific request IDs during status updates or approval checks.
  - **Pre-Flight Overlap API (`GET /api/leave-requests/check-overlap`):**
    - Query parameters: `startDate`, `endDate`, and optional `employeeId`.
    - Returns structured `OverlapCheckResponseDTO` containing `overlapping` boolean, descriptive status message, and full `conflictingRequests` list.
    - Self-service: regular employees query their own records from authenticated context.
    - Elevated access: Managers, HR, and Admin can query for employees under their reporting hierarchy or organization.
    - Validates date ranges and returns HTTP `400 Bad Request` if `endDate < startDate`.

## Validation Architecture Implementation & Verification
- **Status:** Complete
- **Timestamp:** 2026-10-16
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 231 baseline tests passed (0 failures, 0 errors, 0 skipped).
- **Validation Capabilities Implemented & Verified:**
  - **Custom Constraint Validators:**
    - `@ValidDateRange`: Cross-field validation ensuring `endDate >= startDate` and strict calendar year match directly at the DTO layer.
    - `@ValidLeaveBalance`: Cross-field validation enforcing `available <= total` on balance creation/update DTOs.
    - `@ValidStatus`: Allowed enum/status string constraint checking for `ACTIVE`/`INACTIVE` statuses.
  - **DTO-Level Bean Validation Enforcements:**
    - `LeaveRequestDTO`: `@NotNull`, `@Positive` on IDs, `@ValidDateRange`, `@Size(max=500)` on reason.
    - `LeaveBalanceRequestDTO`: `@NotNull`, `@Positive` on IDs, `@Min(0)` on days, `@ValidLeaveBalance`.
    - `EmployeeRequestDTO`: `@NotBlank`, `@Email`, `@ValidStatus`, `@Positive` on salary, departmentId, and managerId.
    - `DepartmentRequestDTO`: `@NotBlank`, `@Size(max=100)`.
    - `LeaveTypeRequestDTO`: `@NotBlank`, `@Min(1)`, `@Max(365)`.
    - `RejectRequestDTO`: `@NotBlank`, `@Size(max=500)` on reason.
    - `PasswordChangeRequestDTO`: `@NotBlank`, `@Size(min=6, max=100)`.
  - **Controller & Handler Integration:**
    - `@Validated` applied across all controllers for query and path variable validation (`@Positive` on IDs).
    - `GlobalExceptionHandler` captures both field errors (`fieldErrors` map) and global class-level errors, formatting clear, localized error messages with HTTP 400 Bad Request.
    - Dedicated exception handlers for `ConstraintViolationException` and `HandlerMethodValidationException`.

## Leave Apply (Leave Request Module) Implementation & Verification
- **Status:** Complete
- **Timestamp:** 2026-10-16
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 213 baseline tests passed.
- **Features & Capabilities Implemented & Verified:**
  - **Self-Service Leave Application (`POST /api/leave-requests`):** Regular employees automatically resolve `employeeId` from the authenticated security context (`SecurityAuthorizationService.getAuthenticatedEmployee(authentication)`). Prevents ID spoofing attempts by overriding any client-supplied `employeeId`. HR/Admin can apply on behalf of employees or default to themselves if unspecified.
  - **Self-Service Leave History (`GET /api/leave-requests/me`):** Authenticated employees securely fetch paginated leave requests with optional status and year filtering without exposing other employees' records (`@PreAuthorize("@securityService.isSelf(authentication, #employeeId) or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_HR')")`).
  - **Multi-Criteria Leave Search (`GET /api/leave-requests`):** Supports dynamic filtering across `employeeId`, `status`, `startDate`, `endDate`, `leaveTypeId`, `departmentId`, and pagination.
  - **Business Rules & Validations:**
    - Working days calculation excluding Saturdays and Sundays (`IllegalArgumentException` if request contains only weekend days).
    - Interval overlap detection preventing concurrent overlapping `PENDING` or `APPROVED` leaves.
    - Active employee and active leave type verification (`400 Bad Request` if inactive).
    - Strict single-calendar-year boundary (`400 Bad Request` if cross-year).
    - Sufficient balance validation against applicable year (`400 Bad Request` if insufficient).
  - **Approval, Rejection & Cancellation Lifecycle:**
    - Dual HTTP method support: both `PUT` and `PATCH` supported for `/{id}/approve`, `/{id}/reject`, and `/{id}/cancel` (ensuring compatibility with Postman and automation scripts).
    - Mandatory rejection reason: rejecting requires non-blank reason parameter (`400 Bad Request` if omitted).
    - Safe cancellation: cancels pending or approved leaves and accurately restores used and available days on approved leaves.
    - Double-cancellation protection: returns `400 Bad Request` if request is already cancelled.
    - Cancellation security: `@PreAuthorize("@securityService.canCancelLeaveRequest(authentication, #id)")` enforces that non-admin/HR users must possess `LEAVE_CANCEL` and be the owner of the leave request (`403 Forbidden` if unauthorized employee attempts cancellation).

## Summary of All Verified Test Suites (277 Total Tests)
| Test Suite | Tests Run | Passed | Failed | Errors | Skipped |
|---|---|---|---|---|---|
| `AuthControllerIntegrationTest` | 9 | 9 | 0 | 0 | 0 |
| `DepartmentControllerIntegrationTest` | 3 | 3 | 0 | 0 | 0 |
| `DynamicPermissionIntegrationTest` | 1 | 1 | 0 | 0 | 0 |
| `EmployeeControllerIntegrationTest` | 5 | 5 | 0 | 0 | 0 |
| `LeaveBalanceControllerIntegrationTest` | 26 | 26 | 0 | 0 | 0 |
| `LeaveRequestControllerIntegrationTest` | 41 | 41 | 0 | 0 | 0 |
| `LeaveTypeControllerIntegrationTest` | 16 | 16 | 0 | 0 | 0 |
| `ProfileSecurityIntegrationTest` | 20 | 20 | 0 | 0 | 0 |
| `RolePermissionControllerIntegrationTest` | 7 | 7 | 0 | 0 | 0 |
| `TeamHierarchyIntegrationTest` | 8 | 8 | 0 | 0 | 0 |
| `ValidationIntegrationTest` | 20 | 20 | 0 | 0 | 0 |
| `LeaveRequestRepositoryTest` | 5 | 5 | 0 | 0 | 0 |
| `DepartmentServiceTest` | 7 | 7 | 0 | 0 | 0 |
| `EmployeeServiceTest` | 5 | 5 | 0 | 0 | 0 |
| `LeaveBalanceServiceTest` | 35 | 35 | 0 | 0 | 0 |
| `LeaveRequestServiceConcurrencyIntegrationTest` | 3 | 3 | 0 | 0 | 0 |
| `LeaveRequestServiceTest` | 49 | 49 | 0 | 0 | 0 |
| `LeaveTypeServiceTest` | 17 | 17 | 0 | 0 | 0 |
| **Total** | **277** | **277** | **0** | **0** | **0** |


## Leave Balances Implementation
- **Status:** Complete
- **Timestamp:** 2026-10-13
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 174 tests passed (0 failures, 0 errors, 0 skipped), including 44 tests dedicated to Balances across `LeaveBalanceServiceTest` (24 tests) and `LeaveBalanceControllerIntegrationTest` (20 tests).
- **Features Verified:**
  - **Self-Service Balance Retrieval (`GET /api/leave-balances/me`):** Authenticated employees securely fetch their own balances directly from the security context without exposing identifier manipulation.
  - **Annual Balance Tracking & Filtering:** Full support for `year` attribute across repository, DTO, entity, and controller layers (`GET /api/leave-balances/employee/{employeeId}?year=YYYY` and `GET /api/leave-balances/me?year=YYYY`).
  - **Default Balance Initialization (`POST /api/leave-balances/employee/{employeeId}/initialize`):** Idempotently populates default balances for all active leave types for an employee for the target or current year with audit logging (`BALANCE_INITIALIZED`).
  - **Active Leave Type Enforcement:** Balance creation strictly validates that the associated leave type is active (`active = true`); inactive types reject creation with `400 Bad Request`.
  - **Deletion Protection:** Deletion of balances is prevented when `PENDING` or `APPROVED` leave requests exist for that employee and leave type (`400 Bad Request` with `"Cannot delete leave balance with pending or approved leave requests"`), safeguarding audit and request history.
  - **Synchronized Usage & Balances:** During request approvals, `available` decrements and `used` increments; during cancellations of approved requests, `available` increments and `used` decrements, maintaining accurate audit records.
  - **Optimistic Concurrency & Double-Refund Protection:** Concurrency tests verify that simultaneous approvals and cancellations correctly enforce `@Version` and `OPTIMISTIC_FORCE_INCREMENT`, preventing double refunds or race conditions.
  - **Role-Based Access Control (RBAC):** Balance management endpoints (`POST`, `PUT`, `DELETE`, `initialize`) require `LEAVE_BALANCE_MANAGE` authority (`ADMIN`/`HR`). Viewing employee balances requires `canViewEmployee` (self, manager hierarchy, or admin/HR). Unauthorized or cross-employee access attempts return `401 Unauthorized` or `403 Forbidden`.
  - **Audit Logging:** Mutations generate structured audit logs with `@AuditAction` (`BALANCE_CREATED`, `BALANCE_UPDATED`, `BALANCE_DELETED`, `BALANCE_INITIALIZED`).

## Leave Types Implementation
- **Status:** Complete
- **Timestamp:** 2026-10-13
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 133 tests passed (0 failures, 0 errors, 0 skipped), including 33 tests across `LeaveTypeServiceTest` (17 tests) and `LeaveTypeControllerIntegrationTest` (16 tests).
- **Features Verified:**
  - **Full Leave Type Lifecycle:** Create (`POST /api/leave-types`), Read all (`GET /api/leave-types`), Read by ID (`GET /api/leave-types/{id}`), Update (`PUT /api/leave-types/{id}`), and Delete (`DELETE /api/leave-types/{id}`).
  - **Status Management:** Toggle active/inactive states via dedicated endpoints (`PUT /api/leave-types/{id}/activate` and `PUT /api/leave-types/{id}/deactivate`).
  - **Active Leave Type Filtering:** Employees and clients can query active leave types via `GET /api/leave-types?active=true` to populate leave application forms without exposing inactive types.
  - **Validation & Constraint Integrity:** Enforces `@NotBlank` and unique leave type names (`409 Conflict` on duplicates during creation or update), and positive default days (`@NotNull`, `@Min(1)`).
  - **Deletion Protection:** Prevents deletion of leave types that are referenced by existing employee leave balances or leave requests (`400 Bad Request` with `"Cannot delete leave type with existing balances or requests"`), eliminating database integrity violations.
  - **Role-Based Access Control (RBAC):** Mutation endpoints (`POST`, `PUT`, `DELETE`, `activate`, `deactivate`) require `LEAVE_TYPE_MANAGE` authority (granted to `ADMIN` and `HR` roles). Standard employees attempting mutation are blocked with `403 Forbidden`. Authenticated employees can read leave types (`isAuthenticated()`). Unauthenticated requests are rejected with `401 Unauthorized`.
  - **Audit Logging:** Every leave type mutation is logged with `@AuditAction` (`LEAVE_TYPE_CREATED`, `LEAVE_TYPE_UPDATED`, `LEAVE_TYPE_DELETED`, `LEAVE_TYPE_ACTIVATED`, `LEAVE_TYPE_DEACTIVATED`).

## Phase 1 Security and API Review
- **Status:** Complete
- **Timestamp:** 2026-10-12
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 108 tests passed (0 failures, 0 errors).
- **Core Security Controls Verified:**
  - **Approval & Rejection Permission Separation:** Verified bidirectional separation (`LEAVE_APPROVE` vs `LEAVE_REJECT`). Removing `LEAVE_APPROVE` allows rejection while blocking approval with `403 Forbidden`; removing `LEAVE_REJECT` allows approval while blocking rejection with `403 Forbidden` (`DynamicPermissionIntegrationTest`).
  - **Transactional Role Permission Integrity:** Verified `PUT /api/roles/{id}/permissions` with nonexistent/invalid permission IDs fails (`404 Not Found`) and rolls back without modifying existing role permissions (`RolePermissionControllerIntegrationTest`).
  - **Dynamic & Active/Inactive Permission Enforcement:** Verified deactivating a permission immediately blocks HTTP access with `403 Forbidden`, and re-activating it immediately restores access (`DynamicPermissionIntegrationTest`).
  - **Manager & Team Scope Enforcement:** Verified strict manager isolation on `/api/managers/me/team`, `/api/managers/{id}/team`, `/api/managers/{id}/team-hierarchy`, and `/api/employees/{id}/hierarchy`. Cross-manager access attempts return `403 Forbidden` (`TeamHierarchyIntegrationTest`).
  - **Hierarchy Subordinate Counting Cycle Protection:** Implemented cycle protection in `EmployeeService.countSubordinates` and `buildTeamHierarchy` by seeding root manager into visited tracker to prevent self-counting and infinite loops. Verified via `testSubordinateCountingCycleProtection` in `TeamHierarchyIntegrationTest`.
  - **Department Hierarchy Authorization Boundaries:** Tested and verified that `/api/departments/{id}/hierarchy` and `/api/departments/hierarchy` reject unauthenticated callers (`401 Unauthorized`), forbid standard employees lacking `DEPARTMENT_VIEW` (`403 Forbidden`), and permit authorized managers and admins (`200 OK`).
  - **Employee Profile Restriction:** Confirmed employees can update only permitted profile fields (`firstName`, `lastName`, `phone`). Administrative attributes (`roles`, `salary`, `status`, `department`, `manager`) cannot be modified by employees and return `403 Forbidden` (`ProfileSecurityIntegrationTest`).
  - **Password Change Security & Refresh Token Revocation:**
    - Current-password validation enforced (`400 Bad Request` on incorrect password).
    - BCrypt secure hashing for new credentials.
    - Refresh token revocation: All active refresh tokens for the user are immediately revoked upon password change (`refreshTokenService.deleteByUserId(user.getId())`), preventing session extension. Verified via regression tests in `AuthControllerIntegrationTest` and `ProfileSecurityIntegrationTest`.
    - Access token lifecycle: Existing stateless JWT access tokens expire naturally within their configured short TTL of 15 minutes (`app.jwt.expirationMs=900000`).
  - **Inactive Account JWT Rejection:** Updated `JwtAuthenticationFilter` to reject inactive accounts immediately with `401 Unauthorized`, even when presenting a previously issued, unexpired access JWT. Verified via integration test `testDeactivatedAccountCannotAccessProtectedEndpointsWithExistingJwtToken`.
  - **Duplicate Endpoint Deprecation:** Deprecated redundant `POST /api/employees/me/change-password` while keeping canonical `PUT /api/employees/me/password` and `POST /api/auth/change-password` to preserve backward compatibility.

## Team Hierarchy Implementation
- **Status:** Complete
- **Timestamp:** 2026-10-11
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 84 tests passed (0 failures, 0 errors), including full suite of tests in `TeamHierarchyIntegrationTest`.
- **Features Verified:**
  - Manager viewing own team (`GET /api/managers/me/team`).
  - Strict isolation: Managers cannot view another manager's team (`GET /api/managers/{id}/team` returns `403 Forbidden`).
  - Hierarchical Team Tree retrieval (`GET /api/managers/me/team-hierarchy`, `GET /api/managers/{id}/team-hierarchy`).
  - Invalid manager assignment prevention (Self-assignment prohibited, inactive manager assignment prohibited, circular reporting chains prevented with cycle detection).
  - Department hierarchy with manager and employee trees (`GET /api/departments/{id}/hierarchy`, `GET /api/departments/hierarchy`).
  - Employee reporting chain traversal upwards & subordinates downwards (`GET /api/employees/{id}/hierarchy`).
  - Department employee transfer handling manager disassociation and team updates (`PUT /api/employees/{id}/department`).
  - Team leave request scoping across direct and department reporting lines.

## Profile Security Implementation
- **Status:** Complete
- **Timestamp:** 2026-10-11
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 103 tests passed (0 failures, 0 errors), including full suite of 19 test cases in `ProfileSecurityIntegrationTest`.
- **Features Verified:**
  - **Self-Service Profile Access (`GET /api/employees/me`):** Authenticated user retrieves their own profile directly from security context without exposing identifier manipulation.
  - **Self-Service Profile Modification (`PUT /api/employees/me/profile`):** Authenticated user updates permitted fields (`firstName`, `lastName`, `phone`).
  - **ID-Based Profile Self-Update (`PUT /api/employees/{id}/profile`):** Validates `@securityService.isSelf(authentication, #id)` so employee can only update their own profile.
  - **Cross-Employee Profile Access Isolation:** Employee cannot view another employee's profile (`GET /api/employees/{id}` returns `403 Forbidden`).
  - **Cross-Employee Profile Tampering Prevention:** Employee cannot update another employee's profile (`PUT /api/employees/{id}/profile` returns `403 Forbidden`).
  - **Administrative Field Protection:** Employee cannot modify `role`, `department`, `manager`, `status`, or `salary` via `PUT /api/employees/{id}` (`403 Forbidden` due to strict `EMPLOYEE_UPDATE` authority requirement).
  - **Privilege Escalation Prevention:** Employees cannot change role via `PATCH /api/employees/{id}/role` (`403 Forbidden`), status via `PATCH /api/employees/{id}/status` (`403 Forbidden`), or manager via `PATCH /api/employees/{id}/manager` (`403 Forbidden`).
  - **Inactive Account Protection:** Deactivated/inactive employees (`status != 'ACTIVE'`) are strictly blocked from updating their profile (`409 Conflict`).
  - **Secure Password Change (`POST /api/employees/me/change-password`, `POST /api/auth/change-password`, `PUT /api/employees/me/password`):**
    - Verifies existing password before allowing modification (`400 Bad Request` if incorrect).
    - Rejects identical old/new password (`400 Bad Request`).
    - Enforces length validation (minimum 8 characters).
    - Upon successful change, invalidates old credentials (login with old password returns `401 Unauthorized`, new password returns `200 OK`).
    - Full audit logging (`PASSWORD_CHANGED`) with zero exposure of credentials or passwords in logs.
  - **Unauthenticated Protection:** Calling `/api/employees/me`, `/api/employees/me/profile`, `/api/employees/me/change-password`, or `/api/auth/change-password` without credentials returns `401 Unauthorized`.

