# Leave Management System

## 1. Project Description
The Leave Management System is a comprehensive RESTful backend application designed to streamline how companies manage employee time off. It empowers employees to seamlessly apply for leave and enables managers to review, approve, or reject these requests while maintaining strict, automated control over leave balances. It supports advanced features such as weekend exclusion during leave calculation, and hierarchical department management.

## 2. Tech Stack
* **Language:** Java 21
* **Framework:** Spring Boot 4.1.0 (Web, Data JPA, Validation)
* **Database:** MySQL (Production), H2 (Testing)
* **Migrations:** Liquibase
* **Testing:** JUnit 5, Mockito, MockMvc, Spring Boot Test
* **Documentation:** Swagger / OpenAPI 3

## 3. Database Configuration
The application relies on a relational database. By default, it expects a MySQL database named `leave_management_db` running on `localhost:3306`. Update `src/main/resources/application.properties` with your database credentials:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/leave_management_db
spring.datasource.username=your_username
spring.datasource.password=your_password
```
For the test profile, an in-memory H2 database is automatically configured in `application-test.properties`.

## 4. Setup Instructions
1. Ensure **Java 21** and **MySQL** are installed locally.
2. Clone the repository and navigate into the root directory.
3. Create a MySQL database named `leave_management_db`.
4. Open a terminal and run the application using Maven Wrapper:
   ```bash
   ./mvnw clean spring-boot:run
   ```
5. The application will start on port 8080.

## 5. Liquibase
The database schema is entirely managed through Liquibase. On application startup, Liquibase reads `src/main/resources/db/changelog/db.changelog-master.yaml` and executes the necessary SQL to keep the database perfectly in sync with the application's entity models. All schema changes, including constraints and indexes, are version-controlled in the `db/changelog/changes` directory.

## 6. API Endpoints
The application exposes the following key modules:
- **Employees**: `/api/employees` (Create, View, Update, Search, Transfer, Status)
- **Departments**: `/api/departments` (Create, View, Update, Delete, View Employees)
- **Leave Types**: `/api/leave-types` (Create, View, Update, Delete, Activate/Deactivate)
- **Leave Balances**: `/api/leave-balances` (Allocate balance, View balance per employee)
- **Leave Requests**: `/api/leave-requests` (Apply, Approve, Reject, Cancel, Filter, Paginate)

## 7. Swagger URL
An interactive Swagger UI is bundled directly within the application. It visualizes the OpenAPI specifications and allows you to test endpoints directly from your browser without needing external tools.
Access it here: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) or [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

## 8. Postman Usage
A comprehensive Postman Collection (`postman_collection.json`) is included in the root directory. 
1. Open Postman.
2. Click **Import** and select `postman_collection.json`.
3. The collection contains folders for all 5 modules with pre-configured success and failure requests.
4. Ensure the application is running locally on port 8080 before executing the requests.

## 9. Testing Instructions
The project includes an extensive suite of unit and integration tests (39+ tests) covering edge cases, validations, business logic, and API endpoint integration. Tests run isolated against an H2 database.
To run the tests:
```bash
./mvnw clean test
```
All tests must pass successfully before committing any changes.
