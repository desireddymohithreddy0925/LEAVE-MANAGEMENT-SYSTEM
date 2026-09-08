1. Complete Database Design
                         ┌───────────────┐
                         │     roles     │
                         ├───────────────┤
                         │ PK id         │
                         │    name       │
                         └───────┬───────┘
                                 │
                                 │
                         ┌───────▼───────┐
                         │  user_roles   │
                         ├───────────────┤
                         │ PK user_id    │
                         │ PK role_id    │
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
                         │ updated_at    │
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
                         │ salary         │
                         │ created_at     │
                         │ updated_at     │
                         └───────┬────────┘
                                 │
                 ┌───────────────┼────────────────┐
                 │               │                │
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
                          │ reason        │  │ available    │
                          │ status        │  └──────────────┘
                          │ rejection_reason
                          │ created_at
                          │ updated_at
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


        ┌─────────────────────┐
        │   refresh_tokens     │
        ├─────────────────────┤
        │ PK id               │
        │ user_id FK          │
        │ token_hash          │
        │ expires_at          │
        │ revoked             │
        │ created_at          │
        └─────────────────────┘


        ┌─────────────────────┐
        │     audit_logs      │
        ├─────────────────────┤
        │ PK id               │
        │ user_id FK          │
        │ action              │
        │ entity_type         │
        │ entity_id           │
        │ old_value           │
        │ new_value           │
        │ ip_address          │
        │ timestamp           │
        └─────────────────────┘

This covers the task's required minimum tables: users, roles, user_roles, employees, departments, leave_types, leave_balances, leave_requests, refresh_tokens, and audit_logs.

2. users

This is the authentication identity.

users
--------------------------------
id                  BIGINT PK
email               VARCHAR(255) UNIQUE
password_hash       VARCHAR(255)
active              BOOLEAN
created_at          DATETIME
updated_at          DATETIME
Important

Don't put employee business information directly into users.

Keep:

users
   ↓
authentication

employees
   ↓
employee/business information
3. roles
roles
-----------------------
id          BIGINT PK
name        VARCHAR(50) UNIQUE

Initial data:

ADMIN
HR
MANAGER
EMPLOYEE

These are the four roles required by your specification.

4. user_roles

Because a user can potentially have multiple roles, use a junction table.

user_roles
-----------------------
user_id     BIGINT FK
role_id     BIGINT FK

PRIMARY KEY (user_id, role_id)

Relationship:

users
  │
  │ 1
  │
  └──────< user_roles >──────┐
                              │
                              │
                              1
                             roles

This is cleaner than putting role directly inside users.

5. employees

This stores employee-specific information.

employees
----------------------------------
id                  BIGINT PK
user_id             BIGINT FK UNIQUE
employee_code       VARCHAR(50) UNIQUE
first_name          VARCHAR(100)
last_name           VARCHAR(100)
department_id       BIGINT FK
manager_id          BIGINT FK
salary              DECIMAL(12,2)
status              VARCHAR(30)
created_at          DATETIME
updated_at          DATETIME

Possible status:

ACTIVE
INACTIVE

Relationship:

User
 │
 │ 1:1
 ▼
Employee
 │
 ├──── Department
 │
 └──── Manager (Employee)

The employee requirements specifically separate employee profile fields from administrative fields such as role, department, manager, status and salary, which should not be freely modifiable by an employee.

6. departments
departments
-----------------------------
id              BIGINT PK
name            VARCHAR(100) UNIQUE
manager_id      BIGINT FK
active          BOOLEAN
created_at      DATETIME
updated_at      DATETIME

Relationship:

Department
    │
    │ 1
    │
    └──────────< Employees

And:

Department
    │
    └──── Manager

Your specification requires department creation/update/listing, manager assignment and employee transfer.

7. leave_types

Examples:

leave_types
--------------------------------
id                  BIGINT PK
name                VARCHAR(100) UNIQUE
description         VARCHAR(500)
default_days        DECIMAL(5,2)
active              BOOLEAN
created_at          DATETIME
updated_at          DATETIME

Example records:

CASUAL
SICK
ANNUAL
MATERNITY
PATERNITY

You can decide the actual types according to your business requirements.

8. leave_balances

This stores an employee's available leave for a particular leave type and year.

leave_balances
--------------------------------
id                  BIGINT PK
employee_id         BIGINT FK
leave_type_id       BIGINT FK
year                INT
total_days          DECIMAL(5,2)
used_days           DECIMAL(5,2)
available_days      DECIMAL(5,2)
created_at          DATETIME
updated_at          DATETIME

Critical constraint:

UNIQUE(employee_id, leave_type_id, year)

So you cannot accidentally create:

Employee 101
SICK
2026

twice.

9. leave_requests

This is the main transaction table.

leave_requests
---------------------------------------
id                  BIGINT PK
employee_id         BIGINT FK
leave_type_id       BIGINT FK
start_date          DATE
end_date            DATE
working_days        DECIMAL(5,2)
reason              VARCHAR(1000)
status              VARCHAR(30)
rejection_reason    VARCHAR(1000)
approved_by         BIGINT FK
approved_at         DATETIME
created_at          DATETIME
updated_at          DATETIME
Status
PENDING
APPROVED
REJECTED
CANCELLED

State machine:

             ┌───────────┐
             │  PENDING  │
             └─────┬─────┘
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
   APPROVED     REJECTED   CANCELLED

The task explicitly defines the leave lifecycle around these states and requires balance deduction/restoration.

10. refresh_tokens

Do not store raw refresh tokens if you can avoid it. Store a hash.

refresh_tokens
--------------------------------
id              BIGINT PK
user_id         BIGINT FK
token_hash      VARCHAR(255)
expires_at      DATETIME
revoked         BOOLEAN
created_at      DATETIME

Relationship:

User
 │
 └──────< Refresh Tokens

This supports:

LOGIN
  ↓
Access Token + Refresh Token
  ↓
Access expires
  ↓
Refresh
  ↓
New Access Token

The authentication requirements include refresh and logout behavior.

11. audit_logs

This is extremely important for your project.

audit_logs
--------------------------------
id              BIGINT PK
user_id         BIGINT FK
action          VARCHAR(100)
entity_type     VARCHAR(100)
entity_id       BIGINT
old_value       TEXT
new_value       TEXT
ip_address      VARCHAR(45)
timestamp       DATETIME

Example:

user_id       = 12
action        = LEAVE_APPROVED
entity_type   = LEAVE_REQUEST
entity_id     = 458
old_value     = PENDING
new_value     = APPROVED
ip_address    = 192.168.1.10

This lets HR/Admin answer:

Who approved this leave?

When?

What was the old state?

What changed?

The task explicitly requires these audit fields and audit coverage for important operations.

12. Complete Relationships

Here's the important part.

users
  │
  │ 1
  ▼
employees
  │
  ├──────────────────────────────┐
  │                              │
  │ N                            │ N
  ▼                              ▼
departments                 leave_requests
  │                              │
  │                              │ N
  │                              ▼
  │                         leave_types
  │
  └── manager


employees
    │
    │ 1
    ▼
leave_balances
    │
    └────────── leave_types


users
    │
    │ 1
    ├──────────< refresh_tokens
    │
    └──────────< audit_logs


users
    │
    │ N
    ▼
user_roles
    ▲
    │ N
    │
   roles
13. Foreign Keys

I'd use these:

user_roles.user_id
        → users.id

user_roles.role_id
        → roles.id

employees.user_id
        → users.id

employees.department_id
        → departments.id

employees.manager_id
        → employees.id

departments.manager_id
        → employees.id

leave_balances.employee_id
        → employees.id

leave_balances.leave_type_id
        → leave_types.id

leave_requests.employee_id
        → employees.id

leave_requests.leave_type_id
        → leave_types.id

leave_requests.approved_by
        → employees.id

refresh_tokens.user_id
        → users.id

audit_logs.user_id
        → users.id
14. Delete Strategy

This is important for a production database.

I would not use cascading deletes for important business records.

For example:

Employee
   ↓
Leave Requests

You shouldn't be able to delete an employee and accidentally delete all their leave history.

Instead:

Employee → INACTIVE

rather than:

DELETE Employee

Similarly:

LeaveType → active = false
Employee  → status = INACTIVE
Department → active = false

This preserves historical records.

The task specifically calls for database integrity and appropriate delete behavior.

15. Important Unique Constraints

At minimum:

users.email
        UNIQUE

employees.user_id
        UNIQUE

employees.employee_code
        UNIQUE

departments.name
        UNIQUE

leave_types.name
        UNIQUE

(employee_id, leave_type_id, year)
        UNIQUE

And:

PRIMARY KEY(user_id, role_id)

for user_roles.

16. Important Indexes

For performance:

users
 └── INDEX(email)

employees
 ├── INDEX(department_id)
 ├── INDEX(manager_id)
 ├── INDEX(status)
 └── INDEX(employee_code)

leave_requests
 ├── INDEX(employee_id)
 ├── INDEX(leave_type_id)
 ├── INDEX(status)
 ├── INDEX(start_date)
 └── INDEX(employee_id, status)

leave_balances
 └── INDEX(employee_id, leave_type_id)

refresh_tokens
 ├── INDEX(user_id)
 └── INDEX(expires_at)

audit_logs
 ├── INDEX(user_id)
 ├── INDEX(entity_type, entity_id)
 └── INDEX(timestamp)

This will help with your employee/leave filtering and pagination requirements.

17. One Important Design Decision

I recommend not storing available_days as an independent calculated truth if you can derive it safely:

available_days = total_days - used_days

However, for your application, maintaining it as a stored value can be acceptable if every update happens transactionally and the invariant is protected.

A robust approach is:

total_days
    -
used_days
    =
available_days

and enforce the update within the same transaction.

For approval:

available_days -= working_days
used_days      += working_days

For cancellation:

available_days += working_days
used_days      -= working_days

This must be protected against concurrent updates.

18. Final ER Diagram
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
Recommended implementation order

Don't create all entities randomly. Build the database in this order:

PHASE 1
users
roles
user_roles
        ↓
PHASE 2
departments
employees
        ↓
PHASE 3
leave_types
leave_balances
        ↓
PHASE 4
leave_requests
        ↓
PHASE 5
refresh_tokens
        ↓
PHASE 6
audit_logs
        ↓
PHASE 7
indexes + constraints + FK rules
        ↓
PHASE 8
Liquibase master changelog

Then map them with JPA entities, but let Liquibase own the database schema. This gives you a clean separation:

JPA/Hibernate
      ↓
Entity mapping

Liquibase
      ↓
Database schema

MySQL
      ↓
Actual persistence

This is the database foundation I'd use for the project before starting the authentication and REST API implementation.