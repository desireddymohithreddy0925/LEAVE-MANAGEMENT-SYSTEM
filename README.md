# Leave Management System

This is a complete, enterprise-ready Leave Management System backend built with Java and Spring Boot. It handles core employee logic, leave requests, leave balances, and department structures while explicitly enforcing business rules (overlap detection, weekend exclusion, and balance integrity).

## Tech Stack
- **Java 17+**
- **Spring Boot 3.3+** (Web, Data JPA, Validation)
- **MySQL 9.7.1** (running via Docker)
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

## Features Implemented
- Complete Employee and Department tracking.
- Leave Type configurations (e.g. Annual, Sick).
- Leave Balance management with automatic deductions/restorations.
- Strict State Machine transitions (`PENDING` -> `APPROVED` / `REJECTED` / `CANCELLED`).
- Intelligent date calculations (automatically skips weekends).
- Bullet-proof exception handling providing consistent HTTP 400/404/409 errors.
