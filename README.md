# Leave Management System

This is a backend REST API for a Leave Management System built using Spring Boot and Java. It allows employees to apply for different types of leave and enables managers to review, approve, or reject these requests while maintaining accurate leave balances.

## Tech Stack
* Java 21
* Spring Boot (Web, Data JPA, Validation)
* MySQL Database
* Liquibase (Database Migrations)
* JUnit 5 & Mockito (Unit Testing)
* Swagger / OpenAPI (API Documentation)

## How to Run the Project

1. Clone the repository.
2. Ensure you have Java 21 and Maven installed on your machine.
3. Install and configure MySQL. Create a database named `leave_management_db`.
4. Update the `src/main/resources/application.properties` file with your local database username and password if they differ from the defaults.
5. Run the application using the Maven wrapper:
   ```bash
   ./mvnw clean spring-boot:run
   ```

The application will start on `http://localhost:8080`.

## Database Configuration

The project uses MySQL. The schema is automatically managed and version-controlled using Liquibase. 
When the application starts, Liquibase will read the changelogs in `src/main/resources/db/changelog/` and automatically create and manage the necessary tables:
* departments
* employees
* leave_types
* leave_balances
* leave_requests

## API Overview

Swagger UI is configured for this project to provide interactive API documentation. Once the application is running, you can view all available endpoints, request structures, and response formats by visiting:

`http://localhost:8080/swagger-ui/index.html`

The system exposes RESTful endpoints for:
* **Employees:** CRUD operations, duplicate email validation.
* **Departments:** CRUD operations.
* **Leave Types:** Manage different leave categories (e.g., Sick Leave, Casual Leave).
* **Leave Balances:** Track available days per employee per leave type.
* **Leave Requests:** Submit, view, approve, and reject leave applications.

## Important Business Rules

1. **Data Integrity:** Employee emails and Department names must be unique.
2. **Leave Balance Validation:** When an employee applies for leave, the system checks if they have enough available days in their balance for that specific leave type. If not, a `400 Bad Request` is thrown.
3. **Manager Approval Workflow:** 
   * New leave requests are always created with a `PENDING` status.
   * Only `PENDING` requests can be approved or rejected.
   * When a manager approves a request, the requested days are officially deducted from the employee's available leave balance.
   * If a manager rejects a request, the employee's balance is untouched.
4. **Date Validation:** The leave start date cannot be in the past, and the end date cannot occur before the start date.
5. **Standardized Exceptions:** All API errors return a clean, consistent JSON error response detailing the timestamp, status code, error type, and specific message.

## Assumptions Made During Development

1. **Weekend Calculation:** For the core functionality, the system calculates leave duration continuously between start and end dates. Implementing strict exclusion of weekends (Saturdays and Sundays) would require a custom calendar utility class.
2. **Authentication/Authorization:** The current API endpoints for manager approvals (`/approve`, `/reject`) are open. It is assumed that in the next iteration, Spring Security (JWT or session-based) would be implemented to restrict these routes strictly to users holding a `MANAGER` role.
3. **Deletions:** Standard JPA deletes were used for simplicity. In a production scenario, implementing "soft deletes" (an `is_active` boolean flag) on entities like LeaveType or Employee would be preferred to preserve historical leave records.
4. **Overlapping Leaves:** Basic date validation is implemented. Checking for overlapping leave requests (where an employee applies for dates that intersect with an already approved leave) would require a custom JPA query checking between start and end dates.
