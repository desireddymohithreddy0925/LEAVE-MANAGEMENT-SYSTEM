# Leave Management System

This is a complete, enterprise-ready Leave Management System backend built with Java and Spring Boot. It handles core employee logic, leave requests, leave balances, and department structures while explicitly enforcing business rules (overlap detection, weekend exclusion, and balance integrity).

## Tech Stack
- **Java 17+**
- **Spring Boot 3.3+** (Web, Data JPA, Validation)
- **MySQL 9.7.1**
- **Liquibase** (Database Migrations)
- **Maven** (Build Tool)
- **Springdoc OpenAPI** (Swagger API Documentation)

## Setup & Run Instructions

### 1. Database Setup
Start a local MySQL container on port `3306` with the database `leave_management_db`.
Configure the `src/main/resources/application.properties` to point to your database credentials.

### 2. Build & Test
Run the complete automated test suite (58 tests covering all business logic) from the root directory:
```bash
./mvnw clean test
```

### 3. Run Application
Start the Spring Boot server:
```bash
./mvnw spring-boot:run
```
The application will run on `http://localhost:8080`. Liquibase will automatically create all tables and schema constraints on startup.

## API Documentation & Testing

### Swagger UI
Once the application is running, navigate to:
**[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)** to view the interactive API documentation and test endpoints directly from the browser.

### Postman Collection
A comprehensive End-to-End Postman collection is included in this repository: `LMS_E2E_Workflow.postman_collection.json`. 
You can import this directly into Postman to test the full lifecycle of a leave request.

## Security Design (Production-Ready Platform)

The platform enforces robust, server-side security using **Spring Security** and **JWT (JSON Web Tokens)** to ensure a production-ready environment.

- **Authentication:** Stateless authentication utilizing JWT access tokens alongside refresh tokens. Dedicated endpoints for Register, Login, Refresh, and Logout. Passwords are securely hashed using BCrypt.
- **Role-Based Access Control (RBAC):** Strict server-side authorization classifying users into specific roles: `ADMIN`, `HR`, `MANAGER`, and `EMPLOYEE`. Endpoints enforce access dynamically based on role.
- **Identity Context Context:** The API utilizes the authenticated `SecurityContextHolder` to reliably identify the active user rather than trusting client-provided payloads, mitigating ID manipulation.
- **Exception Handling:** Correctly responds with `401 Unauthorized` for unauthenticated requests and `403 Forbidden` for unauthorized attempts.
- **Security Audit Logging:** Comprehensive tracking of security events including login success/failure, logout, token issues, and unauthorized access via Spring Application Events.

## Features Implemented
- Complete Employee and Department tracking.
- Leave Type configurations (e.g. Annual, Sick).
- Leave Balance management with automatic deductions/restorations.
- Strict State Machine transitions (`PENDING` -> `APPROVED` / `REJECTED` / `CANCELLED`).
- Intelligent date calculations (automatically skips weekends).
- Bullet-proof exception handling providing consistent HTTP 400/404/409 errors.
## ER Diagrams

```text
                            ┌──────────────┐
                            │    ROLES     │
                            │──────────────│
                            │ PK id        │
                            │ name         │
                            └──────┬───────┘
                                   │
                                   │
                            ┌──────▼───────┐
                            │  USER_ROLES  │
                            │──────────────│
                            │ PK user_id   │
                            │ PK role_id   │
                            └──────┬───────┘
                                   │
                                   │
┌────────────────┐          ┌─────▼──────┐
│ REFRESH_TOKENS │          │   USERS    │
│────────────────│          │────────────│
│ PK id          │◄─────────│ PK id      │
│ user_id FK     │          │ email      │
│ token_hash     │          │ password   │
│ expires_at     │          │ active     │
│ revoked        │          └─────┬──────┘
└────────────────┘                │
                                  │ 1:1
                                  ▼
                         ┌─────────────────┐
                         │    EMPLOYEES    │
                         │─────────────────│
                         │ PK id           │
                         │ user_id FK      │
                         │ employee_code   │
                         │ department_id FK│
                         │ manager_id FK   │◄─────┐
                         │ status          │      │
                         │ salary          │      │
                         └───────┬─────────┘      │
                                 │                │
                    ┌────────────┼──────────┐     │
                    │            │          │     │
                    ▼            ▼          ▼     │
             ┌────────────┐ ┌──────────┐ ┌──────────────┐
             │DEPARTMENTS │ │  LEAVE   │ │    LEAVE     │
             │            │ │ BALANCES │ │   REQUESTS   │
             │ PK id      │ │          │ │              │
             │ name       │ │ PK id    │ │ PK id        │
             │ manager FK │ │ emp FK   │ │ emp FK       │
             │ active     │ │ type FK  │ │ type FK      │
             └────────────┘ │ year     │ │ start_date   │
                            │ total    │ │ end_date     │
                            │ used     │ │ working_days │
                            │ available│ │ status       │
                            └────┬─────┘ │ reason       │
                                 │       └──────┬───────┘
                                 │              │
                                 └──────┬───────┘
                                        ▼
                                ┌──────────────┐
                                │ LEAVE_TYPES  │
                                │──────────────│
                                │ PK id        │
                                │ name         │
                                │ default_days │
                                │ active       │
                                └──────────────┘


                         ┌────────────────┐
                         │   AUDIT_LOGS   │
                         │────────────────│
                         │ PK id          │
                         │ user_id FK     │
                         │ action         │
                         │ entity_type    │
                         │ entity_id      │
                         │ old_value      │
                         │ new_value      │
                         │ ip_address     │
                         │ timestamp      │
                         └────────────────┘
```

## API Endpoints

### 1. Employees (`/api/employees`)
- `GET /` - Get all employees
- `GET /{id}` - Get employee by ID
- `POST /` - Add a new employee
- `PUT /{id}` - Update employee details
- `PATCH /{id}/status` - Update employee status
- `PUT /{id}/department` - Update employee department
- `DELETE /{id}` - Delete an employee

### 2. Departments (`/api/departments`)
- `GET /` - Get all departments
- `GET /{id}` - Get department by ID
- `GET /{id}/employees` - Get all employees in a department
- `POST /` - Add a new department
- `PUT /{id}` - Update a department
- `DELETE /{id}` - Delete a department

### 3. Leave Types (`/api/leave-types`)
- `GET /` - Get all leave types
- `GET /{id}` - Get leave type by ID
- `POST /` - Create a new leave type
- `PUT /{id}` - Update a leave type
- `PUT /{id}/activate` - Activate a leave type
- `PUT /{id}/deactivate` - Deactivate a leave type
- `DELETE /{id}` - Delete a leave type

### 4. Leave Balances (`/api/leave-balances`)
- `GET /` - Get all leave balances
- `GET /{id}` - Get leave balance by ID
- `GET /employee/{employeeId}` - Get leave balances for a specific employee
- `POST /` - Create a leave balance
- `PUT /{id}` - Update a leave balance
- `DELETE /{id}` - Delete a leave balance

### 5. Leave Requests (`/api/leave-requests`)
- `GET /` - Get all leave requests
- `GET /{id}` - Get leave request by ID
- `GET /employee/{employeeId}` - Get all leave requests for a specific employee
- `GET /status/{status}` - Get leave requests filtered by status
- `POST /` - Submit a new leave request
- `PUT /{id}/approve` - Approve a leave request
- `PUT /{id}/reject` - Reject a leave request
- `PUT /{id}/cancel` - Cancel a leave request
