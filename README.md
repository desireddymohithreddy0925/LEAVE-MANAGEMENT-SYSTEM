# Leave Management System

## 1. Project Overview
The Leave Management System is a comprehensive RESTful backend application designed to streamline how companies manage employee time off. It empowers employees to seamlessly apply for leave and enables managers to review, approve, or reject these requests while maintaining strict, automated control over leave balances.

## 2. Features
* **Employee & Department Management:** Full CRUD capabilities with strict email uniqueness checks.
* **Leave Type Configurations:** Administrators can create different leave categories (e.g., Casual Leave, Sick Leave).
* **Leave Balance Tracking:** Automatically tracks the available days an employee has for specific leave types.
* **Leave Request Lifecycle:** Employees apply for leave, creating a `PENDING` request for manager review.
* **Manager Workflow:** Managers can safely approve or reject leaves. Approvals automatically deduct days from the employee's balance.

## 3. Technology Stack
* **Language:** Java 21
* **Framework:** Spring Boot 3.x (Web, Data JPA, Validation)
* **Database:** MySQL
* **Migrations:** Liquibase
* **Testing:** JUnit 5 & Mockito
* **Documentation:** Swagger / OpenAPI 3

## 4. Project Architecture
The application strictly follows a clean layered architecture pattern:
* **Controllers:** Handle incoming HTTP requests and route them appropriately.
* **Services:** Contain 100% of the core business logic, validations, and rules.
* **Repositories:** Spring Data JPA interfaces for seamless database interaction.
* **Entities:** Represent the database schema using JPA annotations.
* **Exception Handling:** A global, centralized layer to catch and format API errors.

## 5. Project Structure
```text
src/main/java/com/leave_management_system/
 ├── controller/     # REST APIs
 ├── service/        # Business logic
 ├── repository/     # Data access
 ├── entity/         # Database models
 └── exception/      # Custom exceptions and GlobalExceptionHandler
```

## 6. Database Design
The relational schema consists of 5 core tables:
* `departments` (id, name)
* `employees` (id, first_name, last_name, email, department_id)
* `leave_types` (id, name, description, default_days)
* `leave_balances` (id, employee_id, leave_type_id, available_days)
* `leave_requests` (id, employee_id, leave_type_id, start_date, end_date, reason, status)

## 7. API Endpoints
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/employees` | Create a new employee |
| GET | `/api/leave-requests/status/{status}` | View leaves by status (e.g. PENDING) |
| POST | `/api/leave-requests` | Apply for leave |
| PUT | `/api/leave-requests/{id}/approve` | Approve a leave request |
| PUT | `/api/leave-requests/{id}/reject` | Reject a leave request |

## 8. Request & Response Examples
**Example: Apply for Leave (POST /api/leave-requests)**
*Request:*
```json
{
  "employee": { "id": 1 },
  "leaveType": { "id": 1 },
  "startDate": "2026-09-01",
  "endDate": "2026-09-03",
  "reason": "Personal Vacation"
}
```
*Response:*
```json
{
  "id": 1,
  "startDate": "2026-09-01",
  "endDate": "2026-09-03",
  "reason": "Personal Vacation",
  "status": "PENDING"
}
```

## 9. Validation Rules
* **Database Constraints:** Utilizes `@NotBlank`, `@Min`, etc., ensuring no empty names or negative default days.
* **Date Logic:** The system strictly rejects leave requests where the end date occurs before the start date.
* **Duplication:** Email addresses must be entirely unique across the employee pool.

## 10. Business Rules
1. A leave request begins in the `PENDING` state.
2. Only a `PENDING` request can be approved or rejected.
3. Employees cannot apply for leave if their `LeaveBalance` does not have enough available days to cover the requested duration.
4. When a manager `APPROVES` a leave, the total duration is automatically deducted from the balance. Rejections leave the balance untouched.

## 11. Exception Handling
Generic Java stack traces are never exposed. Using `@ControllerAdvice`, custom exceptions (like `InsufficientLeaveException` and `DuplicateResourceException`) are intercepted and formatted into a standard JSON response with appropriate HTTP codes (e.g., 400 Bad Request, 409 Conflict).

## 12. Liquibase Database Migration
The database schema is entirely managed through Liquibase. On application startup, Liquibase reads `src/main/resources/db/changelog/db.changelog-master.yaml` and executes the necessary SQL to keep the database perfectly in sync with the application's entity models.

## 13. Postman Testing
The entire API suite is fully compatible with Postman. You can import the endpoints and execute full end-to-end workflows (Create Employee -> Apply for Leave -> Approve Leave -> Verify Balance).

## 14. Swagger Documentation
An interactive Swagger UI is bundled directly within the application. It visualizes the OpenAPI specifications and allows you to test endpoints directly from your browser without needing external tools.
Access it here: `http://localhost:8080/swagger-ui/index.html`

## 15. How to Run the Project
1. Ensure Java 21 and MySQL are installed locally.
2. Clone the repository and navigate into the root directory.
3. Create a MySQL database named `leave_management_db`.
4. Open a terminal and run: 
   ```bash
   ./mvnw clean spring-boot:run
   ```
5. The application will start on port 8080. Liquibase will auto-generate the database tables.

## 16. Future Enhancements
* **Spring Security:** Implement JWT-based authentication to restrict approval endpoints strictly to users with a `MANAGER` role.
* **Advanced Calendar Logic:** Integrate a utility to automatically exclude weekends (Saturdays/Sundays) and official company holidays from the leave duration calculation.
* **Soft Deletes:** Implement an `is_active` flag for entities instead of hard JPA deletions to preserve historical audit logs.
