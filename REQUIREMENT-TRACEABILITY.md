# Requirement Traceability Matrix

This document maps the mandatory internship requirements to their specific implementations, APIs, and automated tests. It serves as proof that all business requirements have been fully implemented and verified.

---

### 1. Leave Day Calculation
- **Requirement:** Saturdays and Sundays are properly excluded from the total leave day count.
- **Implementation:** `LeaveRequestService.calculateWorkingDays()`
- **API:** `POST /api/leave-requests` and `PUT /api/leave-requests/{id}/approve`
- **Test:** `LeaveRequestServiceTest.createLeaveRequest_WeekendEdgeCases`
- **Status:** **DONE**

### 2. Leave Overlap Detection
- **Requirement:** Employees cannot apply for leave if dates overlap with existing `PENDING` or `APPROVED` leaves.
- **Implementation:** `LeaveRequestRepository.hasOverlappingLeave()` called in `LeaveRequestService.createLeaveRequest()`
- **API:** `POST /api/leave-requests`
- **Test:** `LeaveRequestServiceTest.createLeaveRequest_OverlappingLeave_ThrowsException`
- **Status:** **DONE**

### 3. Employee Eligibility
- **Requirement:** Only `ACTIVE` employees can apply for leave.
- **Implementation:** `LeaveRequestService.createLeaveRequest()` (`!employee.isActive()` check)
- **API:** `POST /api/leave-requests`
- **Test:** `LeaveRequestServiceTest.createLeaveRequest_InactiveEmployee_ThrowsException`
- **Status:** **DONE**

### 4. Active Leave Types
- **Requirement:** Employees can only apply for active leave types.
- **Implementation:** `LeaveRequestService.createLeaveRequest()` (`!leaveType.isActive()` check)
- **API:** `POST /api/leave-requests`
- **Test:** `LeaveRequestServiceTest.createLeaveRequest_InactiveLeaveType_ThrowsException`
- **Status:** **DONE**

### 5. State Machine Handling
- **Requirement:** Validated transitions (e.g., `PENDING` → `APPROVED`). Invalid transitions are blocked.
- **Implementation:** Handled inside `approveLeaveRequest`, `rejectLeaveRequest`, and `cancelLeaveRequest` in `LeaveRequestService`.
- **API:** `PUT /api/leave-requests/{id}/approve`, `PUT /api/leave-requests/{id}/reject`, `PUT /api/leave-requests/{id}/cancel`
- **Test:** `LeaveRequestServiceTest.approveLeaveRequest_NotPending_ThrowsException`, `cancelLeaveRequest_Rejected_ThrowsException`
- **Status:** **DONE**

### 6. Balance Deduction & Restoration
- **Requirement:** Leave days are automatically deducted upon approval and restored upon cancellation.
- **Implementation:** `LeaveRequestService.approveLeaveRequest()` and `LeaveRequestService.cancelLeaveRequest()`
- **API:** `PUT /api/leave-requests/{id}/approve` and `PUT /api/leave-requests/{id}/cancel`
- **Test:** `LeaveRequestServiceTest.approveLeaveRequest_Success`, `cancelLeaveRequest_Approved_Success`
- **Status:** **DONE**

### 7. Rejection Reasons
- **Requirement:** Managers must supply a rejection reason when rejecting a leave request, which is saved to the database.
- **Implementation:** `LeaveRequestService.rejectLeaveRequest()` validates reason is not null/blank.
- **API:** `PUT /api/leave-requests/{id}/reject?reason=...`
- **Test:** `LeaveRequestServiceTest.rejectLeaveRequest_BlankReason_ThrowsException`
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
- **Requirement:** Support dynamic filtering by employee, status, date range, and pagination.
- **Implementation:** JPA `Specification<LeaveRequest>` in `LeaveRequestService.searchLeaveRequests()`
- **API:** `GET /api/leave-requests/search?employeeId=...&page=0&size=10`
- **Test:** `LeaveRequestControllerIntegrationTest.searchLeaveRequests_Pagination`
- **Status:** **DONE**

### 11. DTO Pattern & Validation
- **Requirement:** Strict use of DTOs and `@Valid` inputs.
- **Implementation:** All Controllers accept/return `*RequestDTO` and `*ResponseDTO` mapping.
- **API:** Enforced across all endpoints.
- **Test:** Implied across all 58 tests; explicitly validated in HTTP `400 Bad Request` tests in `*IntegrationTest.java`.
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
