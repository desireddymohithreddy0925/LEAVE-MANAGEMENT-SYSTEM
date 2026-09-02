# Leave Management System

## 1. Project Description
This is a backend application for a Leave Management System built using Spring Boot. It allows employees to apply for leave and managers to approve or reject them. It also keeps track of employee leave balances and makes sure that things like weekends are not counted as leave days.

## 2. Tech Stack
* **Language:** Java 21
* **Framework:** Spring Boot 4.1.0
* **Database:** MySQL for the main app, H2 for testing
* **Database Migrations:** Liquibase
* **Testing:** JUnit 5, Mockito
* **API Documentation:** Swagger / OpenAPI 3

## 3. Database Configuration
The app uses MySQL. You need to have a database named `leave_management_db` running on `localhost:3306`. 

You can update `src/main/resources/application.properties` with your database username and password:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/leave_management_db
spring.datasource.username=root
spring.datasource.password=password
```
For testing, it automatically uses an in-memory H2 database.

## 4. Setup Instructions
1. Make sure you have Java 21 and MySQL installed.
2. Clone the repository.
3. Create a MySQL database called `leave_management_db`.
4. Open a terminal in the project folder and run:
   ```bash
   ./mvnw clean spring-boot:run
   ```
5. The app will start on port 8080.

## 5. Liquibase
The database tables are created automatically using Liquibase. When you run the application, it reads the `src/main/resources/db/changelog/db.changelog-master.yaml` file and creates the necessary tables and constraints in MySQL.

## 6. API Endpoints
These are the main modules in the project:
- **Employees**: `/api/employees` (Create, View, Update, Search, Transfer)
- **Departments**: `/api/departments` (Create, View, Update, Delete)
- **Leave Types**: `/api/leave-types` (Create, View, Update, Delete, Activate/Deactivate)
- **Leave Balances**: `/api/leave-balances` (Manage leave balances for employees)
- **Leave Requests**: `/api/leave-requests` (Apply, Approve, Reject, Cancel, Filter)

## 7. Swagger Documentation
You can see all the API endpoints and test them using Swagger UI.
Once the application is running, open this link in your browser: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## 8. Postman Usage
I have included a `postman_collection.json` file in the project folder.
1. Open Postman.
2. Click Import and select the `postman_collection.json` file.
3. It has all the requests for testing the different APIs.
4. Make sure the Spring Boot app is running on port 8080 before testing.

## 9. Testing
There are unit and integration tests written for this project. They use an H2 database so they won't affect the MySQL database.
To run all the tests, use this command:
```bash
./mvnw clean test
```
All tests should pass without errors.
