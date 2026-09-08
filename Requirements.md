# Requirements

The task is to build a **Production-Ready Secure Leave & Employee Platform** using Spring Boot. The main goal is to go beyond your previous LMS and focus on **security, authentication, authorization, auditability, Docker, CI/CD, testing, and production readiness**.

## 1. Mandatory Tech Stack

You are expected to use:

- Java 17+
- Spring Boot 3.x
- Spring Security
- JWT
- Spring Data JPA
- **MySQL**
- Liquibase
- Maven
- Bean Validation
- DTO pattern
- Global exception handling
- Swagger/OpenAPI
- JUnit 5
- Mockito
- Integration testing
- Docker
- Docker Compose
- GitHub Actions / CI-CD
- Spring Boot Actuator
- Structured logging

---

# 2. User Roles

There are **4 roles**:

### ADMIN

Full system access.

### HR

- Employee management
- Department management
- Leave management
- Balance management

### MANAGER

- View own team
- View team leave requests
- Approve leave
- Reject leave

### EMPLOYEE

- Own profile
- Own leave balance
- Apply leave
- View own leaves
- Cancel own leave

Security must be enforced **server-side**.

For example, an employee cannot simply change:

```
employeeId=123
```

to access someone else's information. The authenticated user must come from the **JWT/SecurityContext**.

---

# 3. Authentication

You must implement:

```
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
```

Authentication requirements:

- BCrypt password hashing
- JWT access token
- Refresh token
- Token expiry
- Invalid token handling
- Expired token handling
- Disabled user handling
- Duplicate email prevention
- Proper authentication exceptions

---

# 4. Authorization / RBAC

Implement role-based access control using Spring Security.

You should use things such as:

```
@PreAuthorize(...)
```

and ensure:

```
No authentication → 401
Authenticated but no permission → 403
```

For example:

```
EMPLOYEE → ADMIN API ❌
MANAGER → Another manager's team ❌
ADMIN → Admin API ✅
```

---

# 5. Employee Management

Implement:

- Create employee
- Update employee
- Get employee
- Search employees
- Pagination
- Activate/deactivate
- Assign department
- Assign manager
- Change role
- Employee profile

### Important security requirement

An employee can update only permitted profile fields.

They **cannot** modify:

```
role
department
manager
account status
salary
administrative fields
```

---

# 6. Department & Manager Hierarchy

The hierarchy should be:

```
Department
    ↓
Manager
    ↓
Employees
```

Implement:

- Create department
- Update department
- List departments
- Assign manager
- Transfer employee
- View manager's team
- Prevent invalid manager assignments

Example:

```
GET /api/managers/me/team
```

A manager must only see **their own team**.

---

# 7. Leave Management

You reuse the business logic you learned from your LMS, but now integrate it with authenticated users.

Required:

- Leave types
- Leave balances
- Apply leave
- Working-day calculation
- Weekend exclusion
- Overlap detection
- Approval
- Rejection
- Cancellation
- Balance deduction
- Balance restoration
- State machine

Lifecycle:

```
             ┌── APPROVED
PENDING ─────┼── REJECTED
             └── CANCELLED
```

Invalid state transitions must be prevented.

---

# 8. Audit Logging

Every important operation should generate an audit record.

Examples:

```
USER_LOGIN
EMPLOYEE_CREATED
EMPLOYEE_UPDATED
ROLE_CHANGED
LEAVE_APPLIED
LEAVE_APPROVED
LEAVE_REJECTED
LEAVE_CANCELLED
BALANCE_UPDATED
EMPLOYEE_DEACTIVATED
```

Audit information should include:

```
id
user_id
action
entity_type
entity_id
old_value
new_value
ip_address
timestamp
```

Admins should be able to search audit logs.

---

# 9. Security Event Logging

Track:

- Failed login
- Successful login
- Logout
- Expired token
- Invalid token
- Unauthorized API access
- Password change

But **never log**:

```
passwords
JWT secrets
refresh tokens
sensitive credentials
```

---

# 10. Database

Use MySQL consistently throughout the implementation.

Minimum tables:

```
users
roles
user_roles
employees
departments
leave_types
leave_balances
leave_requests
refresh_tokens
audit_logs
```

Use:

- Primary keys
- Foreign keys
- Unique constraints
- Indexes
- NOT NULL constraints
- Appropriate delete behavior
- Database-level integrity
- Liquibase migrations

You also need an **ER diagram** in the README.

---

# 11. API Standards

Successful responses should follow a consistent structure:

```
{
  "success":true,
  "message":"Leave request created successfully",
  "data": {}
}
```

Errors:

```
{
  "success":false,
  "message":"Insufficient leave balance",
  "errorCode":"INSUFFICIENT_BALANCE"
}
```

Do **not** expose stack traces.

---

# 12. Pagination & Filtering

Employee API:

```
GET /api/employees?page=0&size=20
```

Filtering:

```
GET /api/employees?department=IT&status=ACTIVE
```

Leave filtering should support:

```
status
employee
leave type
date range
department
```

---

# 13. Concurrency & Transactions

This is one of the areas where the project goes deeper than your previous LMS.

You must consider:

### Two simultaneous approvals

Only one should successfully deduct the balance.

### Two simultaneous leave applications

Conflicting overlapping requests must be prevented.

### Double cancellation

Balance must **not** be restored twice.

### Concurrent balance update

Balance must remain correct.

Use where justified:

```
@Transactional
Locking
Optimistic locking
Pessimistic locking
Database constraints
```

And document **why** you chose your concurrency strategy.

---

# 14. Testing

Target:

> **80+ meaningful automated tests**
> 

Not just 80 tests for the sake of the number.

### Unit tests

Services and business logic.

### Controller tests

Security + validation + API behavior.

### Repository tests

Important database queries.

### Integration tests

Application + database.

### Security tests

You must test:

### Business tests

```
Leave overlap
Weekend calculation
Insufficient balance
Approval
Rejection
Cancellation
Double approval
Double cancellation
Concurrent operations
```

---

# 15. Swagger / OpenAPI

Swagger must document:

- Authentication
- JWT Bearer authentication
- Request DTOs
- Response DTOs
- Error responses
- Role requirements where useful

The APIs should be testable directly from Swagger.

---

# 16. Docker

Create:

```
Dockerfile
docker-compose.yml
```

Docker Compose should start:

```
Spring Boot Application
        +
MySQL
```

Configuration must come from environment variables.

Never hardcode:

```
DB password
JWT secret
```

---

# 17. Environment Configuration

Support:

```
application.yml
application-dev.yml
application-test.yml
application-prod.yml
```

Environment variables should hold sensitive configuration, for example:

```
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
JWT_EXPIRATION
```

For your MySQL setup, `DB_URL` would point to MySQL.

---

# 18. Actuator

Implement Spring Boot Actuator.

At minimum:

```
/actuator/health
/actuator/info
```

Health should indicate whether the application and required dependencies are working.

---

# 19. CI/CD

Create a GitHub Actions pipeline:

```
Push
 ↓
Checkout
 ↓
Setup Java
 ↓
Maven build
 ↓
Unit tests
 ↓
Integration tests
 ↓
Package
 ↓
Docker build
```

A PR should fail when tests fail.

---

```
No token → 401
Invalid token → 401
Employee → Admin API → 403
Employee → Another employee → 403
Manager → Another team → 403
Admin → Admin API → 200
```

# 20. Logging

Application logs should help identify:

```
request
user
action
result
error
duration
```

But never expose passwords or secrets.

Avoid:

```
System.out.println();
```

Use proper application logging.

---

# 21. Production Documentation

README must contain:

- Project overview
- Architecture
- Tech stack
- Setup
- Environment variables
- Database setup
- Docker setup
- API documentation
- Authentication flow
- Roles/permissions
- Testing
- CI/CD
- Known limitations

Also provide:

```
Architecture diagram
ER diagram
```

---

# 22. Final Repository Structure

Expected structure:

```
src/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── security/
├── exception/
├── mapper/
├── audit/
└── config/

db/
└── changelog/

Dockerfile
docker-compose.yml

.github/
└── workflows/

README.md

Swagger/OpenAPI
Unit tests
Integration tests
Security tests
Postman collection
Architecture diagram
ER diagram
```

---

## In simple terms: what are you actually building?

```
                 CLIENT
                   ↓
            Spring Security
                   ↓
             JWT Authentication
                   ↓
              RBAC / Roles
                   ↓
              Controllers
                   ↓
                DTOs
                   ↓
               Services
                   ↓
            Business Logic
                   ↓
              Repositories
                   ↓
              MySQL
```

Around this core you add:

```
JWT + Refresh Tokens
Audit Logging
Security Events
Liquibase
Swagger
Validation
Exception Handling
Concurrency Protection
80+ Tests
Docker
GitHub Actions
Actuator
Production Logging
Documentation
```

So this is **not just another CRUD Leave Management System**. The main purpose of the task is to demonstrate that you can take your previous LMS business logic and turn it into a **secure, testable, production-style Spring Boot application**.




In shot:-
Build: Production-ready Secure Leave & Employee Platform using Spring Boot.
Tech Stack: Java 17+, Spring Boot 3.x, Spring Security, JWT, JPA, MySQL, Liquibase, Maven, Validation, DTOs, Swagger, JUnit 5, Mockito, Docker, GitHub Actions, Actuator, structured logging.
Roles: ADMIN, HR, MANAGER, EMPLOYEE with server-side RBAC.
Authentication: Register, Login, Refresh, Logout, BCrypt, JWT + Refresh Token, expiry/invalid/disabled-token handling.
Authorization: 401 for unauthenticated, 403 for unauthorized users.
Employee: CRUD, search, pagination, activate/deactivate, department/manager/role assignment, profile management.
Departments: Department management, manager assignment, employee transfer, team hierarchy.
Leave: Leave types, balances, apply, working days, weekends, overlap detection, approve, reject, cancel, balance deduction/restoration, state machine.
Audit: Log important operations with user, action, entity, old/new values, IP and timestamp.
Security Logs: Login success/failure, logout, token issues, unauthorized access, password changes. Never log passwords/secrets/tokens.
Database: Users, roles, user_roles, employees, departments, leave_types, leave_balances, leave_requests, refresh_tokens, audit_logs + constraints/indexes/Liquibase.
API: Standard success/error response format; no stack traces.
Pagination/Filtering: Employees and leave requests.
Concurrency: Handle simultaneous approvals, applications, cancellations and balance updates using transactions/locking/constraints.
Testing: 80+ meaningful tests covering unit, controller, repository, integration, security and concurrency/business scenarios.
Swagger: Document APIs, JWT authentication, DTOs, responses and errors.
Docker: Dockerfile + Compose with Spring Boot + MySQL; sensitive values through environment variables.
Configuration: Dev/Test/Prod profiles and environment-based secrets.
Actuator: /actuator/health, /actuator/info.
CI/CD: GitHub Actions → Build → Tests → Package → Docker build; failed tests must fail PRs.
Logging: Request, user, action, result, error, duration; no secrets.
Documentation: README, architecture diagram, ER diagram, setup, APIs, security, testing, Docker and CI/CD.
Repository: Organized into controller, service, repository, entity, DTO, security, exception, mapper, audit, config, migrations, tests, Docker and CI/CD.

Main goal: Transform the previous LMS into a secure, testable, production-style application, not just another CRUD project.