# Requirements

The task is to build a **Production-Ready Secure Leave & Employee Platform** using Spring Boot. The main goal is to go beyond your previous LMS and focus on **security, authentication, authorization, auditability, Docker, CI/CD, testing, and production readiness**.

## 1. Mandatory Tech Stack

You are expected to use:

- Java 17+
- Spring Boot 3.x
- Spring Security
- JWT
- Spring Data JPA
- **PostgreSQL** — your chosen replacement for the specified MySQL
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

The original specification says MySQL, but since you've decided to use **PostgreSQL**, use PostgreSQL consistently throughout the implementation.

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
PostgreSQL
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

For your PostgreSQL setup, `DB_URL` would point to PostgreSQL rather than MySQL.

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
              PostgreSQL
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



1. Complete System Architecture
                                      ┌───────────────────────┐
                                      │        CLIENTS        │
                                      │                       │
                                      │ Web Application       │
                                      │ Swagger / OpenAPI     │
                                      │ Postman               │
                                      └───────────┬───────────┘
                                                  │
                                             HTTPS / REST
                                                  │
                                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SPRING BOOT APPLICATION                             │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         SECURITY LAYER                                │  │
│  │                                                                       │  │
│  │  JWT Authentication Filter                                           │  │
│  │       ↓                                                               │  │
│  │  JWT Validation                                                       │  │
│  │       ↓                                                               │  │
│  │  SecurityContext                                                      │  │
│  │       ↓                                                               │  │
│  │  Authentication / Authorization                                      │  │
│  │       ↓                                                               │  │
│  │  RBAC + @PreAuthorize                                                 │  │
│  └───────────────────────────────┬───────────────────────────────────────┘  │
│                                  │                                          │
│                                  ▼                                          │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         CONTROLLER LAYER                              │  │
│  │                                                                       │  │
│  │ AuthController                                                        │  │
│  │ EmployeeController                                                    │  │
│  │ DepartmentController                                                  │  │
│  │ ManagerController                                                     │  │
│  │ LeaveController                                                       │  │
│  │ LeaveTypeController                                                   │  │
│  │ LeaveBalanceController                                                │  │
│  │ AuditController                                                       │  │
│  └───────────────────────────────┬───────────────────────────────────────┘  │
│                                  │                                          │
│                                  ▼                                          │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                            DTO LAYER                                  │  │
│  │                                                                       │  │
│  │ Request DTOs → Validation → Service                                   │  │
│  │ Service → Response DTOs → Controller                                 │  │
│  └───────────────────────────────┬───────────────────────────────────────┘  │
│                                  │                                          │
│                                  ▼                                          │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                          SERVICE LAYER                                │  │
│  │                                                                       │  │
│  │ AuthService                                                           │  │
│  │ EmployeeService                                                       │  │
│  │ DepartmentService                                                     │  │
│  │ ManagerService                                                        │  │
│  │ LeaveService                                                          │  │
│  │ LeaveTypeService                                                      │  │
│  │ LeaveBalanceService                                                   │  │
│  │ RefreshTokenService                                                   │  │
│  │ AuditService                                                          │  │
│  └─────────────────────┬───────────────────────┬─────────────────────────┘  │
│                        │                       │                            │
│                        ▼                       ▼                            │
│  ┌──────────────────────────────┐  ┌────────────────────────────────────┐  │
│  │     REPOSITORY LAYER         │  │       CROSS-CUTTING SERVICES      │  │
│  │                              │  │                                    │  │
│  │ UserRepository               │  │ Audit                              │  │
│  │ EmployeeRepository           │  │ Exception Handling                 │  │
│  │ DepartmentRepository         │  │ Logging                            │  │
│  │ LeaveRepository              │  │ Validation                         │  │
│  │ LeaveBalanceRepository       │  │ Transactions                       │  │
│  │ LeaveTypeRepository          │  │ Concurrency / Locking              │  │
│  │ RefreshTokenRepository       │  │                                    │  │
│  │ AuditLogRepository           │  │                                    │  │
│  └──────────────┬───────────────┘  └────────────────────────────────────┘  │
│                 │                                                           │
└─────────────────┼───────────────────────────────────────────────────────────┘
                  │
                  │ JPA / Hibernate
                  ▼
        ┌───────────────────────────┐
        │           MySQL           │
        │                           │
        │ users                     │
        │ roles                     │
        │ user_roles                │
        │ employees                 │
        │ departments               │
        │ leave_types               │
        │ leave_balances            │
        │ leave_requests            │
        │ refresh_tokens             │
        │ audit_logs                │
        └───────────────────────────┘
2. Architecture Style

Use:

Modular Monolith + Layered Architecture

Not microservices.

secure-leave-platform
        │
        ├── Authentication & Security
        ├── Employee Management
        ├── Department Management
        ├── Manager Management
        ├── Leave Management
        ├── Leave Balance
        └── Audit
                │
                ▼
              MySQL

This is ideal for this project because the requirement is one Spring Boot application with Docker, database persistence, testing and CI/CD.

3. Complete Package Structure

I recommend this exact structure:

src/
└── main/
    ├── java/
    │   └── com/
    │       └── mohithreddy/
    │           └── secureleave/
    │
    │               ├── SecureLeaveApplication.java
    │               │
    │               ├── config/
    │               │   ├── SecurityConfig.java
    │               │   ├── OpenApiConfig.java
    │               │   ├── JpaConfig.java
    │               │   └── AuditConfig.java
    │               │
    │               ├── security/
    │               │   ├── JwtAuthenticationFilter.java
    │               │   ├── JwtTokenProvider.java
    │               │   ├── CustomUserDetails.java
    │               │   ├── CustomUserDetailsService.java
    │               │   ├── SecurityContextService.java
    │               │   ├── CustomAuthenticationEntryPoint.java
    │               │   └── CustomAccessDeniedHandler.java
    │               │
    │               ├── controller/
    │               │   ├── AuthController.java
    │               │   ├── EmployeeController.java
    │               │   ├── DepartmentController.java
    │               │   ├── ManagerController.java
    │               │   ├── LeaveController.java
    │               │   ├── LeaveTypeController.java
    │               │   ├── LeaveBalanceController.java
    │               │   └── AuditController.java
    │               │
    │               ├── dto/
    │               │   │
    │               │   ├── auth/
    │               │   │   ├── RegisterRequest.java
    │               │   │   ├── LoginRequest.java
    │               │   │   ├── LoginResponse.java
    │               │   │   ├── RefreshTokenRequest.java
    │               │   │   └── RefreshTokenResponse.java
    │               │   │
    │               │   ├── employee/
    │               │   │   ├── EmployeeCreateRequest.java
    │               │   │   ├── EmployeeUpdateRequest.java
    │               │   │   ├── EmployeeProfileResponse.java
    │               │   │   └── EmployeeResponse.java
    │               │   │
    │               │   ├── department/
    │               │   │   ├── DepartmentCreateRequest.java
    │               │   │   ├── DepartmentUpdateRequest.java
    │               │   │   └── DepartmentResponse.java
    │               │   │
    │               │   ├── leave/
    │               │   │   ├── LeaveRequestCreate.java
    │               │   │   ├── LeaveRejectRequest.java
    │               │   │   ├── LeaveResponse.java
    │               │   │   └── LeaveBalanceResponse.java
    │               │   │
    │               │   └── audit/
    │               │       └── AuditLogResponse.java
    │               │
    │               ├── entity/
    │               │   ├── User.java
    │               │   ├── Role.java
    │               │   ├── Employee.java
    │               │   ├── Department.java
    │               │   ├── LeaveType.java
    │               │   ├── LeaveBalance.java
    │               │   ├── LeaveRequest.java
    │               │   ├── RefreshToken.java
    │               │   └── AuditLog.java
    │               │
    │               ├── repository/
    │               │   ├── UserRepository.java
    │               │   ├── RoleRepository.java
    │               │   ├── EmployeeRepository.java
    │               │   ├── DepartmentRepository.java
    │               │   ├── LeaveTypeRepository.java
    │               │   ├── LeaveBalanceRepository.java
    │               │   ├── LeaveRequestRepository.java
    │               │   ├── RefreshTokenRepository.java
    │               │   └── AuditLogRepository.java
    │               │
    │               ├── service/
    │               │   ├── AuthService.java
    │               │   ├── EmployeeService.java
    │               │   ├── DepartmentService.java
    │               │   ├── ManagerService.java
    │               │   ├── LeaveService.java
    │               │   ├── LeaveTypeService.java
    │               │   ├── LeaveBalanceService.java
    │               │   ├── RefreshTokenService.java
    │               │   └── AuditService.java
    │               │
    │               ├── mapper/
    │               │   ├── UserMapper.java
    │               │   ├── EmployeeMapper.java
    │               │   ├── DepartmentMapper.java
    │               │   └── LeaveMapper.java
    │               │
    │               ├── exception/
    │               │   ├── GlobalExceptionHandler.java
    │               │   ├── ResourceNotFoundException.java
    │               │   ├── BusinessException.java
    │               │   ├── UnauthorizedException.java
    │               │   ├── InsufficientBalanceException.java
    │               │   ├── DuplicateResourceException.java
    │               │   └── InvalidLeaveTransitionException.java
    │               │
    │               ├── audit/
    │               │   ├── AuditEvent.java
    │               │   ├── AuditEventListener.java
    │               │   └── AuditContext.java
    │               │
    │               └── util/
    │                   ├── DateUtils.java
    │                   └── SecurityUtils.java
    │
    └── resources/
        ├── application.yml
        ├── application-dev.yml
        ├── application-test.yml
        ├── application-prod.yml
        │
        └── db/
            └── changelog/
                ├── db.changelog-master.yaml
                ├── 001-create-users.yaml
                ├── 002-create-roles.yaml
                ├── 003-create-employees.yaml
                ├── 004-create-departments.yaml
                ├── 005-create-leave-types.yaml
                ├── 006-create-leave-balances.yaml
                ├── 007-create-leave-requests.yaml
                ├── 008-create-refresh-tokens.yaml
                └── 009-create-audit-logs.yaml

This follows the required repository organization from your task.

4. Authentication Architecture
Registration
POST /api/auth/register
          │
          ▼
   AuthController
          │
          ▼
     AuthService
          │
          ├── Validate email
          ├── Check duplicate
          ├── Hash password
          ├── Assign role
          └── Create user
                  │
                  ▼
             UserRepository
                  │
                  ▼
                MySQL
Login
POST /api/auth/login
          │
          ▼
   AuthController
          │
          ▼
     AuthService
          │
          ▼
   UserRepository
          │
          ▼
    BCrypt Verify
          │
          ▼
   JWT Token Provider
          │
      ┌───┴────┐
      ▼        ▼
 Access JWT  Refresh Token

The requirements explicitly call for registration, login, refresh, logout, BCrypt, access tokens, refresh tokens, expiry and invalid/expired token handling.

5. JWT Request Architecture

After login:

Client
   │
   │ Authorization: Bearer <JWT>
   ▼
JwtAuthenticationFilter
   │
   ├── Extract token
   │
   ├── Validate signature
   │
   ├── Check expiration
   │
   ├── Extract username/user ID
   │
   ├── Load user
   │
   └── Create Authentication
             │
             ▼
       SecurityContext
             │
             ▼
        Controller

The critical rule is:

DO NOT:
employeeId = request.getEmployeeId()

Instead:

SecurityContext
       ↓
Authenticated User
       ↓
Employee

This prevents an employee from changing an ID and accessing another employee's data.

6. RBAC Architecture
                     USER
                      │
                      ▼
                     ROLE
                      │
        ┌─────────────┼──────────────┐
        │             │              │
        ▼             ▼              ▼
      ADMIN           HR          MANAGER
        │             │              │
        │             │              └── Team
        │             │                  Leave
        │             │
        │             └── Employees
        │                 Departments
        │                 Leave
        │                 Balances
        │
        └── Everything
             
             EMPLOYEE
                │
                ├── Own Profile
                ├── Own Balance
                └── Own Leave

Example:

@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
@PreAuthorize("hasRole('MANAGER')")
7. Employee Architecture
                 Employee API
                      │
                      ▼
              EmployeeController
                      │
                      ▼
               EmployeeService
                      │
          ┌───────────┼───────────┐
          ▼           ▼           ▼
     Department     Manager      User
       Check         Check       Check
          │           │           │
          └───────────┼───────────┘
                      ▼
              EmployeeRepository
                      │
                      ▼
                    MySQL

Employee management includes creation, update, search, pagination, activation/deactivation, department assignment, manager assignment, role changes and profile management.

8. Department / Manager Architecture
                Department
                    │
                    ▼
                 Manager
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
    Employee    Employee    Employee

Example:

GET /api/managers/me/team

Flow:

JWT
 ↓
SecurityContext
 ↓
Current Manager
 ↓
ManagerService
 ↓
Find employees belonging to manager
 ↓
Return ONLY that manager's team

The task specifically requires managers to see only their own team.

9. Leave Architecture

This is the main business module.

Employee
   │
   │ POST /api/leaves
   ▼
LeaveController
   │
   ▼
LeaveService
   │
   ├── Get authenticated employee
   │
   ├── Validate employee active
   │
   ├── Validate leave type
   │
   ├── Validate dates
   │
   ├── Calculate working days
   │
   ├── Exclude weekends
   │
   ├── Check overlapping leaves
   │
   ├── Check leave balance
   │
   ▼
Create PENDING Leave
   │
   ▼
LeaveRequestRepository
   │
   ▼
MySQL
   │
   ▼
Audit Event

The required leave features include working-day calculation, weekend exclusion, overlap detection, approval, rejection, cancellation, balance deduction/restoration and a state machine.

10. Leave State Machine
                     ┌──────────────┐
                     │   APPROVED   │
                     └──────────────┘
                            ▲
                            │
                            │
                     ┌──────┴───────┐
                     │    PENDING   │
                     └──────┬───────┘
                            │
                 ┌──────────┴──────────┐
                 ▼                     ▼
          ┌─────────────┐       ┌─────────────┐
          │  REJECTED   │       │  CANCELLED  │
          └─────────────┘       └─────────────┘

Allowed:

PENDING → APPROVED
PENDING → REJECTED
PENDING → CANCELLED

No invalid transitions.

11. Balance Architecture

For approval:

PENDING Leave
      │
      ▼
Check Balance
      │
      ▼
Lock Balance Row
      │
      ▼
Deduct Days
      │
      ▼
APPROVED
      │
      ▼
Audit

For cancellation:

APPROVED Leave
      │
      ▼
CANCEL
      │
      ▼
Lock Balance
      │
      ▼
Restore Days
      │
      ▼
CANCELLED
      │
      ▼
Audit

The task specifically requires protection against double approval, double cancellation and concurrent balance updates.

12. Concurrency Architecture

For important operations:

@Transactional
      │
      ▼
Load record
      │
      ▼
Lock / Version Check
      │
      ▼
Validate current state
      │
      ▼
Perform operation
      │
      ▼
Update database
      │
      ▼
Commit

For example:

Manager A ──┐
            ├──→ Leave Request
Manager B ──┘

              ↓

         Database Lock
              ↓
       First transaction
              ↓
          APPROVED
              ↓
       Balance deducted

       Second transaction
              ↓
       State already changed
              ↓
           REJECTED

Your task explicitly asks you to document why you selected your locking/concurrency strategy.

13. Audit Architecture

I recommend using Spring Application Events for audit processing.

Service
   │
   │ Business operation
   ▼
Publish AuditEvent
   │
   ▼
AuditEventListener
   │
   ▼
AuditService
   │
   ▼
AuditLogRepository
   │
   ▼
MySQL

Example:

LEAVE_APPROVED
EMPLOYEE_CREATED
EMPLOYEE_UPDATED
ROLE_CHANGED
LEAVE_REJECTED
LEAVE_CANCELLED
BALANCE_UPDATED
EMPLOYEE_DEACTIVATED

The audit table should capture user, action, entity, entity ID, old value, new value, IP address and timestamp.

14. Exception Architecture
                Controller
                    │
                    ▼
                 Service
                    │
                    ▼
               Exception
                    │
                    ▼
        GlobalExceptionHandler
                    │
                    ▼
           Standard Error DTO

Example:

{
  "success": false,
  "message": "Insufficient leave balance",
  "errorCode": "INSUFFICIENT_BALANCE"
}

Never return:

500
Java stack trace
SQL exception
Internal class names

The task explicitly requires standardized API errors and no stack traces exposed to clients.

15. Database Architecture
                           MySQL
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
          ▼                  ▼                  ▼
        users              roles           departments
          │                  │                  │
          │                  ▼                  │
          │              user_roles             │
          │                                     │
          ▼                                     ▼
      employees ─────────────────────────── manager
          │
          ├───────────────┐
          │               │
          ▼               ▼
    leave_requests    leave_balances
          │               │
          ▼               ▼
      leave_types     balance records

      refresh_tokens

      audit_logs

The minimum database model specified in the task includes these ten tables.

16. Liquibase Architecture

Don't manually create production tables.

Use:

db/changelog/
        │
        ├── db.changelog-master.yaml
        │
        ├── 001-create-users.yaml
        ├── 002-create-roles.yaml
        ├── 003-create-employees.yaml
        ├── 004-create-departments.yaml
        ├── 005-create-leave-types.yaml
        ├── 006-create-leave-balances.yaml
        ├── 007-create-leave-requests.yaml
        ├── 008-create-refresh-tokens.yaml
        └── 009-create-audit-logs.yaml

Flow:

Liquibase
    ↓
Master Changelog
    ↓
Individual Changesets
    ↓
MySQL Schema
17. API Architecture

Organize APIs by module:

/api/auth/**
/api/employees/**
/api/departments/**
/api/managers/**
/api/leaves/**
/api/leave-types/**
/api/leave-balances/**
/api/audit-logs/**

For example:

Authentication

POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout


Employees

POST   /api/employees
GET    /api/employees
GET    /api/employees/{id}
PUT    /api/employees/{id}
PATCH  /api/employees/{id}/activate
PATCH  /api/employees/{id}/deactivate


Manager

GET /api/managers/me/team


Leaves

POST   /api/leaves
GET    /api/leaves
GET    /api/leaves/{id}
POST   /api/leaves/{id}/approve
POST   /api/leaves/{id}/reject
POST   /api/leaves/{id}/cancel

The exact endpoint names can be finalized during API design; the task specifies the authentication endpoints and the manager team endpoint explicitly.

18. Validation Architecture
HTTP Request
     │
     ▼
Request DTO
     │
     ▼
@Valid
     │
     ├── @NotBlank
     ├── @Size
     ├── @Email
     ├── @NotNull
     ├── @Positive
     └── Custom validation
     │
     ▼
Controller
     │
     ▼
Service

This prevents invalid input from entering the business layer.

19. Logging Architecture

Use:

SLF4J
 +
Logback

Flow:

Request
  ↓
Logging
  ↓
Security
  ↓
Controller
  ↓
Service
  ↓
Database
  ↓
Response
  ↓
Logging

Useful information:

request
user
action
result
error
duration

Never log passwords, JWT secrets or refresh tokens.

20. Actuator Architecture
Spring Boot
     │
     ▼
Spring Actuator
     │
     ├── /actuator/health
     │
     └── /actuator/info

Health should also reflect required dependencies.

21. Testing Architecture

Keep tests separated:

src/test/java/
└── com/mohithreddy/secureleave/

    ├── controller/
    │   ├── AuthControllerTest
    │   ├── EmployeeControllerTest
    │   └── LeaveControllerTest
    │
    ├── service/
    │   ├── AuthServiceTest
    │   ├── EmployeeServiceTest
    │   └── LeaveServiceTest
    │
    ├── repository/
    │   ├── EmployeeRepositoryTest
    │   └── LeaveRepositoryTest
    │
    ├── security/
    │   ├── JwtAuthenticationTest
    │   ├── AuthorizationTest
    │   └── AccessControlTest
    │
    └── integration/
        ├── AuthIntegrationTest
        ├── EmployeeIntegrationTest
        └── LeaveIntegrationTest

Testing layers:

Unit Tests
     ↓
Controller Tests
     ↓
Repository Tests
     ↓
Integration Tests
     ↓
Security Tests
     ↓
Concurrency Tests

The target is 80+ meaningful automated tests.

22. Docker Architecture
                   Docker Compose
                         │
             ┌───────────┴───────────┐
             │                       │
             ▼                       ▼
      ┌──────────────┐        ┌──────────────┐
      │ Spring Boot  │        │     MySQL    │
      │   Container  │◄──────►│   Container  │
      └──────────────┘        └──────────────┘
             │
             ▼
       Environment
       Variables

Environment variables:

DB_URL
DB_USERNAME
DB_PASSWORD

JWT_SECRET
JWT_EXPIRATION

The task requires Dockerfile, Docker Compose, environment-based configuration and specifically prohibits hardcoded database passwords/JWT secrets.

23. CI/CD Architecture
Developer
    │
    ▼
   Git
    │
    ▼
 GitHub
    │
    ▼
GitHub Actions
    │
    ├── Checkout
    │
    ├── Setup Java
    │
    ├── Maven Build
    │
    ├── Unit Tests
    │
    ├── Integration Tests
    │
    ├── Package
    │
    └── Docker Build

A failed test should cause the pipeline/PR check to fail.

24. Final Production Architecture

Everything together:

                                  USERS
                                    │
                                    ▼
                          ┌─────────────────┐
                          │ Web / API Client│
                          └────────┬────────┘
                                   │
                                   ▼
                              HTTPS / REST
                                   │
                                   ▼
                 ┌────────────────────────────────┐
                 │         SPRING BOOT            │
                 │                                │
                 │ ┌────────────────────────────┐ │
                 │ │     SPRING SECURITY        │ │
                 │ │                            │ │
                 │ │ JWT Filter                 │ │
                 │ │ Authentication             │ │
                 │ │ SecurityContext            │ │
                 │ │ RBAC                       │ │
                 │ └──────────────┬─────────────┘ │
                 │                │               │
                 │                ▼               │
                 │ ┌────────────────────────────┐ │
                 │ │       CONTROLLERS          │ │
                 │ └──────────────┬─────────────┘ │
                 │                │               │
                 │                ▼               │
                 │ ┌────────────────────────────┐ │
                 │ │           DTOs             │ │
                 │ │ Validation / Mapping       │ │
                 │ └──────────────┬─────────────┘ │
                 │                │               │
                 │                ▼               │
                 │ ┌────────────────────────────┐ │
                 │ │         SERVICES           │ │
                 │ │                            │ │
                 │ │ Auth                       │ │
                 │ │ Employee                   │ │
                 │ │ Department                 │ │
                 │ │ Manager                    │ │
                 │ │ Leave                      │ │
                 │ │ Balance                    │ │
                 │ │ Audit                      │ │
                 │ └──────────────┬─────────────┘ │
                 │                │               │
                 │                ▼               │
                 │ ┌────────────────────────────┐ │
                 │ │       REPOSITORIES         │ │
                 │ └──────────────┬─────────────┘ │
                 │                │               │
                 └────────────────┼───────────────┘
                                  │
                              JPA/Hibernate
                                  │
                                  ▼
                         ┌──────────────────┐
                         │      MySQL       │
                         │                  │
                         │ users            │
                         │ roles            │
                         │ user_roles       │
                         │ employees        │
                         │ departments      │
                         │ leave_types      │
                         │ leave_balances   │
                         │ leave_requests   │
                         │ refresh_tokens   │
                         │ audit_logs       │
                         └──────────────────┘


        ┌────────────────────────────────────────────────────┐
        │              CROSS-CUTTING CONCERNS                │
        │                                                    │
        │ Validation │ Exceptions │ Logging │ Auditing       │
        │ Transactions │ Locking │ State Machine             │
        │                                                    │
        └────────────────────────────────────────────────────┘


        ┌────────────────────────────────────────────────────┐
        │                    DEVOPS                          │
        │                                                    │
        │ Docker → Docker Compose → GitHub Actions           │
        │                                                    │
        └────────────────────────────────────────────────────┘


        ┌────────────────────────────────────────────────────┐
        │                 MONITORING                         │
        │                                                    │
        │ Spring Boot Actuator                               │
        │ /actuator/health                                   │
        │ /actuator/info                                     │
        │                                                    │
        └────────────────────────────────────────────────────┘
25. The architecture you should actually build

If we simplify the whole project into 10 major modules, build them in this order:

01. Project Foundation
        ↓
02. Database + Liquibase
        ↓
03. User + Role Management
        ↓
04. Authentication + JWT
        ↓
05. Spring Security + RBAC
        ↓
06. Employee + Department + Manager
        ↓
07. Leave + Balance + State Machine
        ↓
08. Audit + Logging
        ↓
09. Testing + Concurrency
        ↓
10. Docker + CI/CD + Actuator + Production Config

This also aligns closely with the task's 75-day progression from architecture/database foundation through authentication, authorization, employee/leave modules, audit, concurrency, testing, Docker/CI/CD and final production audit.


Recommended final stack:

Frontend/API Client
        ↓
Spring Boot 3.x
        ↓
Spring Security + JWT
        ↓
REST Controllers
        ↓
DTO + Validation
        ↓
Service Layer
        ↓
Spring Data JPA / Hibernate
        ↓
MySQL
        ↓
Liquibase

Supporting:
Swagger
JUnit 5
Mockito
Integration Tests
Docker
Docker Compose
GitHub Actions
Actuator
SLF4J/Logback

That is the architecture I would use for my project—production-style, secure, modular, testable, and still manageable as a single Spring Boot application.