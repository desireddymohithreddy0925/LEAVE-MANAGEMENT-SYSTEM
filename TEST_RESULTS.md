# Test Results

## Cross-Year Leave Request Validation Verification
- **Status:** Complete
- **Timestamp:** 2026-10-15
- **Regression Suite:** `mvn clean test` executed successfully.
- **Result:** All 199 tests passed (0 failures, 0 errors, 0 skipped), including 35 tests covering leave request lifecycle, annual balance isolation, and cross-year boundary validation across `LeaveRequestServiceTest` (30 tests) and `LeaveRequestControllerIntegrationTest` (5 tests).
- **Features & Regressions Verified:**
  - **Cross-Year Leave Request Rejection:** Validates that `startDate` and `endDate` must belong to the exact same calendar year. Requests spanning multiple calendar years (e.g., across December and January) are rejected upfront with a clear validation error: `"Leave request cannot span across multiple calendar years. Please submit separate requests for each year."`.
  - **Single-Year Balance Protection:** Eliminates the edge case where working days across a boundary year would be charged entirely to the starting year's balance, preserving strict annual balance accounting.
  - **Defensive Safeguards on Approval and Cancellation:** Added defensive cross-calendar-year assertions in `approveLeaveRequest` and `cancelLeaveRequest` to ensure corrupt or cross-year requests cannot deduct or refund days.
  - **Regression Testing:**
    - Unit regression tests in `LeaveRequestServiceTest`:
      - `createLeaveRequest_Regression_CrossCalendarYearDates_ThrowsException`: Proves application fails when `startDate` and `endDate` fall in different calendar years, preventing balance lookup and save operations.
      - `approveLeaveRequest_Regression_CrossCalendarYearDates_ThrowsException`: Proves approval fails when dates span different years.
      - `cancelLeaveRequest_Regression_CrossCalendarYearDates_ThrowsException`: Proves cancellation fails when dates span different years.
    - Integration regression test in `LeaveRequestControllerIntegrationTest`:
      - `createLeaveRequest_CrossCalendarYearDates_Returns400BadRequest`: Validates over HTTP endpoint that submitting a request spanning December and January returns HTTP `400 Bad Request` with structured error details.

## Summary of All Verified Test Suites (199 Total Tests)
| Test Suite | Tests Run | Passed | Failed | Errors | Skipped |
|---|---|---|---|---|---|
| `AuthControllerIntegrationTest` | 9 | 9 | 0 | 0 | 0 |
| `DepartmentControllerIntegrationTest` | 3 | 3 | 0 | 0 | 0 |
| `DynamicPermissionIntegrationTest` | 1 | 1 | 0 | 0 | 0 |
| `EmployeeControllerIntegrationTest` | 5 | 5 | 0 | 0 | 0 |
| `LeaveBalanceControllerIntegrationTest` | 26 | 26 | 0 | 0 | 0 |
| `LeaveRequestControllerIntegrationTest` | 5 | 5 | 0 | 0 | 0 |
| `LeaveTypeControllerIntegrationTest` | 16 | 16 | 0 | 0 | 0 |
| `ProfileSecurityIntegrationTest` | 20 | 20 | 0 | 0 | 0 |
| `RolePermissionControllerIntegrationTest` | 7 | 7 | 0 | 0 | 0 |
| `TeamHierarchyIntegrationTest` | 8 | 8 | 0 | 0 | 0 |
| `LeaveRequestRepositoryTest` | 2 | 2 | 0 | 0 | 0 |
| `DepartmentServiceTest` | 7 | 7 | 0 | 0 | 0 |
| `EmployeeServiceTest` | 5 | 5 | 0 | 0 | 0 |
| `LeaveBalanceServiceTest` | 35 | 35 | 0 | 0 | 0 |
| `LeaveRequestServiceConcurrencyIntegrationTest` | 3 | 3 | 0 | 0 | 0 |
| `LeaveRequestServiceTest` | 30 | 30 | 0 | 0 | 0 |
| `LeaveTypeServiceTest` | 17 | 17 | 0 | 0 | 0 |
| **Total** | **199** | **199** | **0** | **0** | **0** |

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

