# Architecture and API Documentation
## Leave Management System (LMS) — System Architecture & REST API Specification

**Document Classification:** System Architecture & API Blueprint  
**Release Target:** v1.0.0-RELEASE  
**Date:** November 21, 2026  
**Auditor / Architecture Lead:** Antigravity Architecture Team  
**Specification Standard:** OpenAPI 3.0 / Springdoc Swagger UI  

---

## 1. High-Level System Architecture

The Leave Management System is designed as a secure, stateless, containerized Spring Boot backend backed by a relational PostgreSQL database:

```
                                [Client Ingress]
                                       │
                                       ▼ (TCP / HTTPS)
       ┌───────────────────────────────────────────────────────────────┐
       │                 Reverse Proxy / Ingress Gateway               │
       │                   (Nginx / AWS ALB / Traefik)                 │
       └───────────────────────────────┬───────────────────────────────┘
                                       │ (X-Forwarded-For + Client IP)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ lms-public-net                                                              │
│   ┌───────────────────────────────────────────────────────────────────────┐ │
│   │ lms-backend (Spring Boot 4.1.0 on Temurin 21 JRE, UID 10001:10001)    │ │
│   │                                                                       │ │
│   │   [ClientIpResolver] ──> [JwtAuthenticationFilter] ──> [Filter Chain] │ │
│   │                                                                       │ │
│   │   [IdempotencyService] ──> [GlobalExceptionHandler] ──> [Controllers]  │ │
│   │                                                               │       │ │
│   │   [CryptoService (AES-256-GCM)] <── [Domain Services] ────────┘       │ │
│   │                                           │                           │ │
│   │   [Liquibase Migrations]                  │ (HikariCP Pool)           │ │
│   └───────────────────────────────────────────┼───────────────────────────┘ │
└───────────────────────────────────────────────┼─────────────────────────────┘
                                                │
┌───────────────────────────────────────────────┼─────────────────────────────┐
│ lms-internal-net (internal: true)             ▼                             │
│   ┌───────────────────────────────────────────────────────────────────────┐ │
│   │ lms-postgres (PostgreSQL 15 Alpine, Port 5432)                        │ │
│   │   - 20 Base Tables, 29 Changesets, 25 Foreign Keys                    │ │
│   │   - Row-Level Pessimistic Locking (SELECT ... FOR UPDATE)             │ │
│   │   - Mounted Volume: lms_postgres_data                                 │ │
│   └───────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Security & Request Pipeline

Every incoming HTTP request traverses a hardened security filter pipeline:

1. **Client IP Resolution (`ClientIpResolver`)**:
   - Parses `X-Forwarded-For` from right to left against `SECURITY_TRUSTED_PROXIES`. Discards spoofed headers if ingress connection is untrusted.
2. **Idempotency Filter (`IdempotencyFilter`)**:
   - For mutating requests (`POST`, `PUT`, `DELETE`), checks `Idempotency-Key` header. Returns cached response for duplicate requests; returns `409 Conflict` if an operation is currently in-flight.
3. **JWT Authentication (`JwtAuthenticationFilter`)**:
   - Validates Bearer token authenticity, signature, and expiration. Confirms that `userDetails.isEnabled() == true`. Rejects disabled or revoked users immediately with `401 Unauthorized`.
4. **Method Security (`@PreAuthorize`)**:
   - Evaluates required permissions dynamically against database-persisted roles (`ROLE_ADMIN`, `ROLE_HR`, `ROLE_MANAGER`, `ROLE_EMPLOYEE`).
5. **Business Scope Enforcement**:
   - Managers restricted to direct/indirect subordinates. Employees restricted to self-service data.

---

## 3. Database Entity Relationship (ER) Blueprint

```
                      ┌───────────────┐          ┌────────────────────┐
                      │     roles     │          │    permissions     │
                      ├───────────────┤          ├────────────────────┤
                      │ PK id         │          │ PK id              │
                      │    name       │          │    name UNIQUE     │
                      └───────┬───────┘          └─────────┬──────────┘
                              │                            │
                              │                   ┌────────▼──────────┐
                      ┌───────▼───────┐           │  role_permissions │
                      │  user_roles   │           ├───────────────────┤
                      ├───────────────┤           │ PK role_id        │
                      │ PK user_id    │           │ PK permission_id  │
                      │ PK role_id    │           └───────────────────┘
                      └───────┬───────┘
                              │
                      ┌───────▼───────┐
                      │     users     │
                      ├───────────────┤
                      │ PK id         │
                      │ email UNIQUE  │
                      │ password_hash │
                      │ active        │
                      │ created_at    │
                      └───────┬───────┘
                              │
                              │ 1:1
                              ▼
                      ┌────────────────┐
                      │   employees    │
                      ├────────────────┤
                      │ PK id          │
                      │ FK user_id     │
                      │ employee_code  │
                      │ first_name     │
                      │ last_name      │
                      │ department_id  │
                      │ manager_id     │
                      │ status         │
                      │ salary (ENC)   │
                      └───────┬────────┘
                              │
              ┌───────────────┼────────────────┐
              │               │                │
              ▼               ▼                ▼
     ┌──────────────┐  ┌───────────────┐  ┌──────────────┐
     │ departments  │  │ leave_requests│  │leave_balances│
     ├──────────────┤  ├───────────────┤  ├──────────────┤
     │ PK id        │  │ PK id         │  │ PK id        │
     │ name UNIQUE  │  │ employee_id   │  │ employee_id  │
     │ manager_id   │  │ leave_type_id │  │ leave_type_id│
     │ active       │  │ start_date    │  │ year         │
     └──────────────┘  │ end_date      │  │ total_days   │
                       │ days          │  │ used_days    │
                       │ reason (ENC)  │  │ available    │
                       │ status        │  └──────────────┘
                       │ version       │
                       └───────┬───────┘
                               │
                               ▼
                      ┌─────────────────┐
                      │   leave_types   │
                      ├─────────────────┤
                      │ PK id           │
                      │ name UNIQUE     │
                      │ description     │
                      │ default_days    │
                      │ active          │
                      └─────────────────┘
```

---

## 4. REST API Endpoint Reference

All REST endpoints return standardized JSON payloads with ISO-8601 timestamps. Interactive OpenAPI documentation is accessible at **`http://localhost:8080/swagger-ui.html`**.

### 4.1 Authentication & Profile (`/api/auth`, `/api/employees`)
| Method | Endpoint | Authority | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register new employee account. |
| `POST` | `/api/auth/login` | Public | Authenticate with email/password; returns JWT access + refresh tokens. |
| `POST` | `/api/auth/refresh` | Public | Exchange refresh token for fresh access token. |
| `POST` | `/api/auth/logout` | Authenticated | Revoke refresh token and terminate active session. |
| `POST` | `/api/auth/change-password` | Authenticated | Change account password; automatically revokes all active refresh tokens. |
| `GET` | `/api/employees/me` | Authenticated | Self-service endpoint to view current user's employee profile. |
| `PUT` | `/api/employees/me` | Authenticated | Update self-service fields (`firstName`, `lastName`, `phone`). |
| `GET` | `/api/employees` | `EMPLOYEE_READ` | Paginated listing of employees (scoped by role/department). |
| `GET` | `/api/employees/{id}` | `EMPLOYEE_READ` | Retrieve specific employee by ID (scoped). |
| `PUT` | `/api/employees/{id}/unlock` | `ADMIN` / `HR` | Unlock account following failed-attempt lockout. |

### 4.2 Department Governance (`/api/departments`)
| Method | Endpoint | Authority | Description |
|---|---|---|---|
| `POST` | `/api/departments` | `DEPARTMENT_MANAGE` | Create new department with designated manager. |
| `GET` | `/api/departments` | `DEPARTMENT_READ` | List all departments. |
| `GET` | `/api/departments/{id}` | `DEPARTMENT_READ` | Get department details. |
| `GET` | `/api/departments/{id}/hierarchy` | `DEPARTMENT_READ` | Get tree hierarchy of department, managers, and subordinates. |
| `PUT` | `/api/departments/{id}` | `DEPARTMENT_MANAGE` | Update department name or manager assignment. |
| `DELETE` | `/api/departments/{id}` | `DEPARTMENT_MANAGE` | Deactivate/remove department (cycle-protected). |

### 4.3 Leave Types (`/api/leave-types`)
| Method | Endpoint | Authority | Description |
|---|---|---|---|
| `POST` | `/api/leave-types` | `LEAVE_TYPE_MANAGE` | Define leave category (`ANNUAL`, `SICK`, `MATERNITY`, etc.). |
| `GET` | `/api/leave-types` | Authenticated | List all leave types (supports `?active=true` filter). |
| `PUT` | `/api/leave-types/{id}` | `LEAVE_TYPE_MANAGE` | Update leave type parameters and default quota. |
| `DELETE`| `/api/leave-types/{id}` | `LEAVE_TYPE_MANAGE` | Deactivate leave type (FK-protected from deletion if active). |

### 4.4 Leave Balances (`/api/leave-balances`)
| Method | Endpoint | Authority | Description |
|---|---|---|---|
| `GET` | `/api/leave-balances/me` | Authenticated | Self-service: View current employee's balances for current year. |
| `GET` | `/api/leave-balances` | `LEAVE_BALANCE_READ` | Query balances by employee ID and year (`?year=YYYY`). |
| `POST` | `/api/leave-balances` | `LEAVE_BALANCE_MANAGE`| Create balance allocation for an employee. |
| `POST` | `/api/leave-balances/employee/{id}/initialize` | `LEAVE_BALANCE_MANAGE` | Initialize balances for all active leave types for given employee. |
| `PUT` | `/api/leave-balances/{id}` | `LEAVE_BALANCE_MANAGE`| Adjust quota (`used + available <= total`). |

### 4.5 Leave Requests Workflow (`/api/leave-requests`)
| Method | Endpoint | Authority | Description |
|---|---|---|---|
| `POST` | `/api/leave-requests` | `LEAVE_APPLY` | Submit leave application. Excludes weekends, checks balance, prevents overlap. |
| `GET` | `/api/leave-requests/me` | Authenticated | List leave requests submitted by current user. |
| `GET` | `/api/leave-requests/team` | `LEAVE_APPROVE` | List team leave requests awaiting manager approval. |
| `PUT` | `/api/leave-requests/{id}/approve` | `LEAVE_APPROVE` | Approve leave. Decrements available balance, increments used balance. |
| `PUT` | `/api/leave-requests/{id}/reject` | `LEAVE_REJECT` | Reject leave with mandatory rejection reason. |
| `PUT` | `/api/leave-requests/{id}/cancel` | `LEAVE_CANCEL` | Cancel pending or approved leave. Restores deducted balance. |

### 4.6 System Diagnostics & Health (`/api/audit`, `/actuator`)
| Method | Endpoint | Authority | Description |
|---|---|---|---|
| `GET` | `/api/audit/logs` | `AUDIT_VIEW` | Paginated query of business entity audit logs. |
| `GET` | `/api/audit/performance/query-diagnostics` | `ADMIN` / `SYSTEM_AUDIT` | Query execution time and N+1 prevention metrics. |
| `GET` | `/api/audit/performance/system-summary` | `ADMIN` / `SYSTEM_AUDIT` | JVM heap, CPU load, and HikariCP pool diagnostics. |
| `GET` | `/actuator/health` | Public (Sanitized) | Health check probe returning `{"status":"UP"}`. |

---

## 5. Centralized Error Response Model

All API errors return a standardized JSON structure matching `ErrorResponseDTO`:

```json
{
  "timestamp": "2026-11-21T22:15:30.123Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied: insufficient permission or unauthorized data scope",
  "path": "/api/departments/1/hierarchy"
}
```

Common status codes:
- **`400 Bad Request`**: Malformed payload, failed bean validation, or negative balance math.
- **`401 Unauthorized`**: Missing, invalid, or expired JWT, or deactivated user account.
- **`403 Forbidden`**: Insufficient RBAC permission or attempt to access cross-department data.
- **`404 Not Found`**: Entity does not exist.
- **`409 Conflict`**: Concurrent idempotent request in-flight or overlapping leave dates.
