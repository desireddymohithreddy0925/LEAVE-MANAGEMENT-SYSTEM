# Leave Management System

This is a complete, enterprise-ready Leave Management System backend built with Java and Spring Boot. It handles core employee logic, leave requests, leave balances, and department structures while explicitly enforcing business rules (overlap detection, weekend exclusion, and balance integrity).

## Tech Stack
- **Java 17+**
- **Spring Boot 3.3+** (Web, Data JPA, Validation, Actuator)
- **PostgreSQL 15**
- **Docker & Docker Compose**
- **Liquibase** (Database Migrations)
- **Maven** (Build Tool)
- **Springdoc OpenAPI** (Swagger API Documentation)

## Setup & Run Instructions

### 1. Docker Setup (Recommended)
You can run the entire stack (PostgreSQL + Spring Boot Application) using Docker Compose:
```bash
docker-compose up --build
```
This will start the database and application using `application-prod.yml` configuration. The application will be available on `http://localhost:8080`.

### 2. Local Database Setup (Development)
If you prefer running locally, start a local PostgreSQL container on port `5432` with the database `leave_management_db`.
Configure your environment variables (`DB_USERNAME`, `DB_PASSWORD`) or edit `src/main/resources/application-dev.yml`.

### 3. Build & Test
Run the complete automated test suite (199 tests covering all business logic, security reviews, hierarchy cycle protection, leave types, balance consistency, annual balance isolation, cross-year boundary validation, and RBAC) from the root directory:
```bash
./mvnw clean test
```

### 4. Run Application
Start the Spring Boot server in dev mode:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
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

### Authorization Flow

**Authentication** ➔ **User** ➔ **Roles** ➔ **Permissions** ➔ **Business/Data Scope** ➔ **Authorization Decision**

- **Role**: A grouping of permissions (e.g., `ADMIN`, `HR`, `MANAGER`, `EMPLOYEE`).
- **Permission**: A specific action the user is allowed to do (e.g., `LEAVE_APPROVE`, `EMPLOYEE_VIEW`).
- **Scope**: The specific data the user is allowed to operate on (e.g., A manager can only approve leave for *their own* team members).

A user is only granted access if they possess the required **Permission** AND satisfy the **Business/Data Scope** requirements.

### Key Security Features
- **Stateless Authentication & Token Lifecycle:** JWT access tokens alongside rotating refresh tokens. Dedicated endpoints for Register, Login, Refresh, Logout, and Password Change. Passwords are salted and hashed using BCrypt. Access token TTL is strictly limited to 15 minutes (`app.jwt.expirationMs=900000`).
- **Immediate Refresh Token Revocation:** Upon changing account passwords (`/api/employees/me/password` or `/api/auth/change-password`), all active refresh tokens for the user are immediately revoked in the database (`refreshTokenService.deleteByUserId`), preventing session extension.
- **Inactive Account Protection:** `JwtAuthenticationFilter` validates `userDetails.isEnabled()` on every authenticated request. Inactive accounts are immediately rejected with `401 Unauthorized` even if presenting a previously issued, unexpired access JWT.
- **Dynamic Database-Driven Permissions:** Fine-grained permissions evaluated dynamically at request time (`@PreAuthorize("hasAuthority('...')")`). Permissions can be dynamically activated/deactivated or assigned/removed with instant effect on HTTP requests.
- **Separation of Approval & Rejection:** Discrete `LEAVE_APPROVE` and `LEAVE_REJECT` permissions allow fine-grained administrative role scoping.
- **Self-Service Profile Security:** Dedicated `/api/employees/me` endpoints allow authenticated employees to view and update only allowed profile fields (`firstName`, `lastName`, `phone`). Administrative attributes (`role`, `salary`, `status`, `department`, `manager`) require `EMPLOYEE_UPDATE` permission and reject employee self-modification attempts with `403 Forbidden`.
- **Identity & Scope Enforcement:** Dynamic security service enforces data-level scoping (managers can only access their direct and indirect reporting tree). Cross-manager and cross-employee tampering attempts return `403 Forbidden`.
- **Security Audit Logging:** Comprehensive tracking of security events (`LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `PASSWORD_CHANGED`, `INACTIVE_USER_TOKEN`, `EXPIRED_TOKEN`, `INVALID_TOKEN`) and entity auditing (`audit_logs`) via Spring Application Events.

## Features Implemented
- **Organization & Team Hierarchy:** Multi-level team tree traversal (`GET /api/managers/{id}/team-hierarchy`, `GET /api/employees/{id}/hierarchy`) with cycle protection during subordinate counting and hierarchy construction.
- **Department Hierarchy:** Complete department structure with manager mapping, member lists, and team subtrees (`GET /api/departments/{id}/hierarchy`, `GET /api/departments/hierarchy`).
- **Leave Requests & Balances:** Automated balance deduction and restoration with optimistic locking (`@Version`) preventing race conditions during concurrent approvals.
- **Leave Balances Management & Self-Service:** Complete balances CRUD and self-service retrieval (`GET /api/leave-balances/me`), yearly filtering (`?year=YYYY`), default balance initialization for all active leave types (`POST /api/leave-balances/employee/{employeeId}/initialize`), active leave type enforcement on creation, deletion protection preventing balance removal when pending or approved requests exist (`400 Bad Request`), balance consistency on creation (`total == used + available`), update validation ensuring `used + available <= total`, and synchronized `used`/`available` balance updates during approval and cancellation with optimistic concurrency (`@Version`, `OPTIMISTIC_FORCE_INCREMENT`).
- **State Machine Transitions:** Strict lifecycle transitions (`PENDING` -> `APPROVED` / `REJECTED` / `CANCELLED`) with rejection reason requirements.
- **Intelligent Date Calculations:** Automatically excludes weekends when calculating requested leave duration.
- **Leave Types Management & Protection:** Complete lifecycle management (`POST`, `GET`, `PUT`, `DELETE`, `activate`, `deactivate`) with active-status filtering (`GET /api/leave-types?active=true`), foreign key deletion protection preventing removal of leave types in active use by balances or requests (`400 Bad Request`), and strict RBAC enforcement (`LEAVE_TYPE_MANAGE`).
- **Centralized Exception Handling:** Standardized `ErrorResponseDTO` structure delivering appropriate HTTP 400, 401, 403, 404, and 409 responses.
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

### 1. Authentication & Security (`/api/auth`)
- `POST /register` - Register a new employee user account
- `POST /login` - Authenticate credentials and receive access + refresh JWT
- `POST /refresh` - Rotate and issue new access & refresh tokens
- `POST /logout` - Revoke refresh token and invalidate active session
- `POST /change-password` - Change account password, immediately revoking all refresh tokens

### 2. Employees & Profiles (`/api/employees`)
- `GET /` - Search and page employees (`EMPLOYEE_VIEW`)
- `GET /{id}` - Get employee details by ID
- `POST /` - Add a new employee
- `PUT /{id}` - Update full employee record (Administrative, requires `EMPLOYEE_UPDATE`)
- `DELETE /{id}` - Delete employee record
- `PATCH /{id}/status` - Update employee status (`ACTIVE` / `INACTIVE`)
- `PATCH /{id}/manager` - Assign reporting manager (with circular reference detection)
- `PATCH /{id}/role` - Assign role to employee
- `PUT /{id}/department` - Transfer employee to a department
- `GET /department/{departmentId}` - List employees in a specific department
- `GET /me` - Retrieve authenticated user's profile
- `PUT /me/profile` - Update authenticated user's permitted fields (`firstName`, `lastName`, `phone`)
- `PUT /{id}/profile` - Self-service profile update by ID (only allowed for self)
- `PUT /me/password` - Update authenticated user's password (canonical endpoint)
- `POST /me/change-password` - Deprecated password change endpoint (preserved for backward compatibility)
- `GET /me/hierarchy` - Retrieve current employee's upward reporting chain and direct reports
- `GET /{id}/hierarchy` - Retrieve employee hierarchy (requires self, manager-of, or admin scope)

### 3. Managers & Teams (`/api/managers`)
- `GET /me/team` - Retrieve authenticated manager's direct team members
- `GET /{id}/team` - Retrieve team members of manager ID (strictly isolated across managers)
- `GET /me/team-hierarchy` - Retrieve authenticated manager's complete team tree with cycle protection
- `GET /{id}/team-hierarchy` - Retrieve team tree for manager ID (manager-isolated, requires authorization)

### 4. Departments (`/api/departments`)
- `GET /` - Get all departments
- `GET /{id}` - Get department by ID
- `GET /{id}/employees` - Get all employees in a department
- `POST /` - Add a new department
- `PUT /{id}` - Update department details
- `PATCH /{id}/manager` - Assign department manager
- `DELETE /{id}` - Delete a department
- `GET /{id}/hierarchy` - Get department hierarchy with manager, employees, and team tree (`DEPARTMENT_VIEW`)
- `GET /hierarchy` - Get hierarchy trees for all departments (`DEPARTMENT_VIEW`)

### 5. Roles & Dynamic Permissions (`/api/roles`, `/api/permissions`)
- `GET /api/roles` - Get all system roles
- `GET /api/roles/{id}/permissions` - Get all permissions assigned to a role
- `PUT /api/roles/{id}/permissions` - Replace role permissions (transactional validation against invalid IDs)
- `POST /api/roles/{id}/permissions/{permissionId}` - Add a permission to a role
- `DELETE /api/roles/{id}/permissions/{permissionId}` - Remove a permission from a role
- `GET /api/permissions` - Get all system permissions
- `PATCH /api/permissions/{id}/status` - Activate/deactivate dynamic permission status

### 6. Leave Types (`/api/leave-types`)
- `GET /` - Get all active/available leave types
- `GET /{id}` - Get leave type by ID
- `POST /` - Create a new leave type
- `PUT /{id}` - Update a leave type
- `PATCH /{id}/status` - Activate or deactivate a leave type
- `DELETE /{id}` - Delete a leave type

### 7. Leave Balances (`/api/leave-balances`)
- `GET /employee/{employeeId}` - Get leave balances for a specific employee
- `POST /` - Manually create a leave balance entry
- `PUT /{id}` - Adjust leave balance totals
- `POST /initialize/{employeeId}` - Initialize annual leave quotas for an employee

### 8. Leave Requests (`/api/leave-requests`)
- `GET /my-requests` - Get leave requests submitted by the authenticated employee
- `GET /{id}` - Get leave request by ID (enforces self / manager-of data scope)
- `GET /employee/{employeeId}` - Get leave requests for employee (scoped to employee or their manager)
- `GET /team` - Get leave requests for the authenticated manager's team
- `GET /department/{departmentId}` - Get leave requests for an entire department
- `GET /` - List all leave requests (requires `LEAVE_VIEW_ALL`)
- `POST /` - Submit a new leave request (validates balance, overlap, weekends)
- `PUT /{id}/approve` - Approve a leave request (requires `LEAVE_APPROVE` and manager scope)
- `PUT /{id}/reject` - Reject a leave request with mandatory reason (requires `LEAVE_REJECT` and manager scope)
- `PUT /{id}/cancel` - Cancel a leave request (restoring leave balance quota)
