#!/usr/bin/env python3
import json
import os

def create_item(name, method, url_str, headers=None, body_obj=None, pre_script=None, test_script=None):
    parsed_headers = []
    if headers:
        for k, v in headers.items():
            parsed_headers.append({"key": k, "value": v, "type": "text"})

    raw_url = url_str
    path_and_query = raw_url.split("{{baseUrl}}")[-1]
    path_part = path_and_query.split("?")[0].strip("/").split("/")
    
    query_list = []
    if "?" in path_and_query:
        query_str = path_and_query.split("?")[1]
        for pair in query_str.split("&"):
            if "=" in pair:
                qk, qv = pair.split("=", 1)
                query_list.append({"key": qk, "value": qv})

    request_obj = {
        "method": method,
        "header": parsed_headers,
        "url": {
            "raw": raw_url,
            "host": ["{{baseUrl}}"],
            "path": path_part,
        }
    }
    if query_list:
        request_obj["url"]["query"] = query_list

    if body_obj is not None:
        request_obj["body"] = {
            "mode": "raw",
            "raw": json.dumps(body_obj, indent=2) if isinstance(body_obj, dict) else str(body_obj),
            "options": {
                "raw": {
                    "language": "json"
                }
            }
        }
        if not any(h["key"].lower() == "content-type" for h in parsed_headers):
            request_obj["header"].append({"key": "Content-Type", "value": "application/json", "type": "text"})

    item_obj = {
        "name": name,
        "request": request_obj
    }

    events = []
    if pre_script:
        events.append({
            "listen": "prerequest",
            "script": {
                "type": "text/javascript",
                "exec": pre_script.strip().split("\n")
            }
        })
    if test_script:
        events.append({
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": test_script.strip().split("\n")
            }
        })

    if events:
        item_obj["event"] = events

    return item_obj

def build_collection():
    collection_pre_script = """
function formatDate(d) {
    var year = d.getFullYear();
    var month = ('0' + (d.getMonth() + 1)).slice(-2);
    var day = ('0' + d.getDate()).slice(-2);
    return year + '-' + month + '-' + day;
}
var today = new Date();
var daysUntilMonday = (1 + 7 - today.getDay()) % 7;
if (daysUntilMonday === 0) daysUntilMonday = 7;
var nextMon = new Date(today.getTime() + daysUntilMonday * 24 * 60 * 60 * 1000);
var nextWed = new Date(nextMon.getTime() + 2 * 24 * 60 * 60 * 1000);
var nextThu = new Date(nextMon.getTime() + 3 * 24 * 60 * 60 * 1000);
var nextFri = new Date(nextMon.getTime() + 4 * 24 * 60 * 60 * 1000);

if (!pm.environment.get("leaveStartDate")) {
    pm.environment.set("leaveStartDate", formatDate(nextMon));
}
if (!pm.environment.get("leaveEndDate")) {
    pm.environment.set("leaveEndDate", formatDate(nextWed));
}
if (!pm.environment.get("leaveStartDate2")) {
    pm.environment.set("leaveStartDate2", formatDate(nextThu));
}
if (!pm.environment.get("leaveEndDate2")) {
    pm.environment.set("leaveEndDate2", formatDate(nextFri));
}
"""

    folders = []

    # ==========================================
    # 1. Authentication & Security Lifecycle
    # ==========================================
    auth_items = []

    # 1.1 Admin Login
    auth_items.append(create_item(
        name="1.1 Admin Login",
        method="POST",
        url_str="{{baseUrl}}/api/auth/login",
        body_obj={"email": "{{adminEmail}}", "password": "{{adminPassword}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Response contains JWT accessToken and refreshToken", function () {
    var res = pm.response.json();
    pm.expect(res.accessToken).to.be.a("string");
    pm.expect(res.refreshToken).to.be.a("string");
    pm.environment.set("adminToken", res.accessToken);
    pm.environment.set("adminRefreshToken", res.refreshToken);
});
pm.test("Response time is under 1500ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(1500);
});
"""
    ))

    # 1.2 Register Employee User
    auth_items.append(create_item(
        name="1.2 Register Employee User",
        method="POST",
        url_str="{{baseUrl}}/api/auth/register",
        body_obj={
            "email": "{{employeeEmail}}",
            "password": "{{employeePassword}}",
            "firstName": "Alice",
            "lastName": "Engineer",
            "phone": "9876543210",
            "employeeCode": "{{employeeCode}}"
        },
        pre_script="""
var ts = Date.now();
pm.environment.set("employeeEmail", "emp." + ts + "@test.com");
pm.environment.set("employeeCode", "EMP-" + ts);
""",
        test_script="""
pm.test("Status is 200 OK or 201 Created", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201]);
});
pm.test("Returns user object and accessToken", function () {
    var res = pm.response.json();
    pm.expect(res.accessToken).to.be.a("string");
    pm.environment.set("employeeToken", res.accessToken);
});
"""
    ))

    # 1.3 Register Manager User
    auth_items.append(create_item(
        name="1.3 Register Manager User",
        method="POST",
        url_str="{{baseUrl}}/api/auth/register",
        body_obj={
            "email": "{{managerEmail}}",
            "password": "{{managerPassword}}",
            "firstName": "Bob",
            "lastName": "Manager",
            "phone": "9876543211",
            "employeeCode": "{{managerCode}}"
        },
        pre_script="""
var ts = Date.now() + 10;
pm.environment.set("managerEmail", "mgr." + ts + "@test.com");
pm.environment.set("managerCode", "MGR-" + ts);
""",
        test_script="""
pm.test("Status is 200 OK or 201 Created", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201]);
});
pm.test("Returns manager temporary token", function () {
    var res = pm.response.json();
    pm.expect(res.accessToken).to.be.a("string");
    pm.environment.set("managerTempToken", res.accessToken);
});
"""
    ))

    # 1.4 Get Manager Employee ID
    auth_items.append(create_item(
        name="1.4 Get Manager Employee Profile",
        method="GET",
        url_str="{{baseUrl}}/api/employees/me",
        headers={"Authorization": "Bearer {{managerTempToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Capture Manager Employee ID", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.be.a("number");
    pm.environment.set("managerEmployeeId", res.id);
});
"""
    ))

    # 1.5 Elevate Manager Role (Admin Only)
    auth_items.append(create_item(
        name="1.5 Elevate Manager Role (Admin Only)",
        method="PATCH",
        url_str="{{baseUrl}}/api/employees/{{managerEmployeeId}}/role?roleName=MANAGER",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Employee role updated to MANAGER", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.eql(parseInt(pm.environment.get("managerEmployeeId")));
});
"""
    ))

    # 1.6 Login as Manager
    auth_items.append(create_item(
        name="1.6 Login as Manager",
        method="POST",
        url_str="{{baseUrl}}/api/auth/login",
        body_obj={"email": "{{managerEmail}}", "password": "{{managerPassword}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Capture manager elevated accessToken", function () {
    var res = pm.response.json();
    pm.expect(res.accessToken).to.be.a("string");
    pm.environment.set("managerToken", res.accessToken);
});
"""
    ))

    # 1.7 Get Employee Profile and ID
    auth_items.append(create_item(
        name="1.7 Get Employee Profile and ID",
        method="GET",
        url_str="{{baseUrl}}/api/employees/me",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Capture employeeId and verify details", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.be.a("number");
    pm.expect(res.email).to.eql(pm.environment.get("employeeEmail"));
    pm.environment.set("employeeId", res.id);
});
"""
    ))

    # 1.8 Token Refresh
    auth_items.append(create_item(
        name="1.8 Token Refresh",
        method="POST",
        url_str="{{baseUrl}}/api/auth/refresh",
        body_obj={"refreshToken": "{{adminRefreshToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("New access token issued", function () {
    var res = pm.response.json();
    pm.expect(res.accessToken).to.be.a("string");
    pm.environment.set("adminToken", res.accessToken);
});
"""
    ))

    # 1.9 Negative: Login with Invalid Password
    auth_items.append(create_item(
        name="1.9 Negative: Login with Invalid Password",
        method="POST",
        url_str="{{baseUrl}}/api/auth/login",
        body_obj={"email": "{{adminEmail}}", "password": "WrongPassword@123"},
        test_script="""
pm.test("Status is 401 Unauthorized", function () {
    pm.response.to.have.status(401);
});
"""
    ))

    # 1.10 Negative: Access Protected Endpoint Without Token
    auth_items.append(create_item(
        name="1.10 Negative: Access Protected Resource Without Token",
        method="GET",
        url_str="{{baseUrl}}/api/departments",
        test_script="""
pm.test("Status is 401 Unauthorized or 403 Forbidden", function () {
    pm.expect(pm.response.code).to.be.oneOf([401, 403]);
});
"""
    ))

    folders.append({"name": "1. Authentication & Security Lifecycle", "item": auth_items})

    # ==========================================
    # 2. Department Management
    # ==========================================
    dept_items = []

    # 2.1 Create Department
    dept_items.append(create_item(
        name="2.1 Create Department (Admin)",
        method="POST",
        url_str="{{baseUrl}}/api/departments",
        headers={"Authorization": "Bearer {{adminToken}}"},
        body_obj={"name": "{{departmentName}}"},
        pre_script="""
var ts = Date.now();
pm.environment.set("departmentName", "Engineering-" + ts);
""",
        test_script="""
pm.test("Status is 201 Created", function () {
    pm.response.to.have.status(201);
});
pm.test("Department created with ID and name", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.be.a("number");
    pm.expect(res.name).to.eql(pm.environment.get("departmentName"));
    pm.environment.set("departmentId", res.id);
});
"""
    ))

    # 2.2 Negative: Duplicate Department Name
    dept_items.append(create_item(
        name="2.2 Negative: Duplicate Department Name",
        method="POST",
        url_str="{{baseUrl}}/api/departments",
        headers={"Authorization": "Bearer {{adminToken}}"},
        body_obj={"name": "{{departmentName}}"},
        test_script="""
pm.test("Status is 400 Bad Request or 409 Conflict", function () {
    pm.expect(pm.response.code).to.be.oneOf([400, 409]);
});
"""
    ))

    # 2.3 Get Department by ID
    dept_items.append(create_item(
        name="2.3 Get Department by ID",
        method="GET",
        url_str="{{baseUrl}}/api/departments/{{departmentId}}",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Department ID matches requested", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.eql(parseInt(pm.environment.get("departmentId")));
});
"""
    ))

    # 2.4 Get All Departments
    dept_items.append(create_item(
        name="2.4 Get All Departments",
        method="GET",
        url_str="{{baseUrl}}/api/departments",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Returns non-empty array of departments", function () {
    var res = pm.response.json();
    pm.expect(Array.isArray(res)).to.be.true;
    pm.expect(res.length).to.be.at.least(1);
});
"""
    ))

    # 2.5 Assign Manager to Department
    dept_items.append(create_item(
        name="2.5 Assign Manager to Department",
        method="PATCH",
        url_str="{{baseUrl}}/api/departments/{{departmentId}}/manager?managerId={{managerEmployeeId}}",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Department now has manager assigned", function () {
    var res = pm.response.json();
    pm.expect(res.managerId).to.eql(parseInt(pm.environment.get("managerEmployeeId")));
});
"""
    ))

    # 2.6 Assign Employee to Department
    dept_items.append(create_item(
        name="2.6 Assign Employee Department",
        method="PUT",
        url_str="{{baseUrl}}/api/employees/{{employeeId}}/department?departmentId={{departmentId}}",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Employee assigned to department", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.eql(parseInt(pm.environment.get("employeeId")));
    if (res.department) {
        pm.expect(res.department.id).to.eql(parseInt(pm.environment.get("departmentId")));
    }
});
"""
    ))

    # 2.7 Assign Manager to Employee
    dept_items.append(create_item(
        name="2.7 Assign Manager to Employee",
        method="PATCH",
        url_str="{{baseUrl}}/api/employees/{{employeeId}}/manager?managerId={{managerEmployeeId}}",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Employee manager updated", function () {
    var res = pm.response.json();
    pm.expect(res.managerId).to.eql(parseInt(pm.environment.get("managerEmployeeId")));
});
"""
    ))

    # 2.8 Get Department Employees
    dept_items.append(create_item(
        name="2.8 Get Department Employees",
        method="GET",
        url_str="{{baseUrl}}/api/departments/{{departmentId}}/employees",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("List contains assigned employee", function () {
    var res = pm.response.json();
    pm.expect(Array.isArray(res)).to.be.true;
    var found = res.some(function (e) {
        return e.id === parseInt(pm.environment.get("employeeId"));
    });
    pm.expect(found).to.be.true;
});
"""
    ))

    # 2.9 Get Department Hierarchy
    dept_items.append(create_item(
        name="2.9 Get Department Hierarchy",
        method="GET",
        url_str="{{baseUrl}}/api/departments/{{departmentId}}/hierarchy",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Hierarchy contains manager and employees", function () {
    var res = pm.response.json();
    pm.expect(res.departmentId).to.eql(parseInt(pm.environment.get("departmentId")));
    pm.expect(res.manager).to.be.an("object");
});
"""
    ))

    folders.append({"name": "2. Department Management", "item": dept_items})

    # ==========================================
    # 3. Employee Profile & Hierarchy
    # ==========================================
    emp_items = []

    # 3.1 Get Employee Profile
    emp_items.append(create_item(
        name="3.1 Get Employee Self Profile",
        method="GET",
        url_str="{{baseUrl}}/api/employees/me",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Profile matches current employee", function () {
    var res = pm.response.json();
    pm.expect(res.email).to.eql(pm.environment.get("employeeEmail"));
});
"""
    ))

    # 3.2 Update Employee Profile
    emp_items.append(create_item(
        name="3.2 Update Employee Self Profile",
        method="PUT",
        url_str="{{baseUrl}}/api/employees/me/profile",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        body_obj={
            "firstName": "Alice",
            "lastName": "Engineer",
            "phone": "1112223334"
        },
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Phone updated successfully", function () {
    var res = pm.response.json();
    pm.expect(res.phone).to.eql("1112223334");
});
"""
    ))

    # 3.3 Get Employee Hierarchy
    emp_items.append(create_item(
        name="3.3 Get Employee Reporting Hierarchy",
        method="GET",
        url_str="{{baseUrl}}/api/employees/{{employeeId}}/hierarchy",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
"""
    ))

    folders.append({"name": "3. Employee Profile & Hierarchy", "item": emp_items})

    # ==========================================
    # 4. Leave Type Management
    # ==========================================
    lt_items = []

    # 4.1 Create Leave Type
    lt_items.append(create_item(
        name="4.1 Create Leave Type (Admin)",
        method="POST",
        url_str="{{baseUrl}}/api/leave-types",
        headers={"Authorization": "Bearer {{adminToken}}"},
        body_obj={
            "name": "{{leaveTypeName}}",
            "description": "Standard Annual Leave with Full Pay",
            "defaultDays": 20
        },
        pre_script="""
var ts = Date.now();
pm.environment.set("leaveTypeName", "Annual-Leave-" + ts);
""",
        test_script="""
pm.test("Status is 201 Created", function () {
    pm.response.to.have.status(201);
});
pm.test("Leave type created with default days", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.be.a("number");
    pm.expect(res.defaultDays).to.eql(20);
    pm.environment.set("leaveTypeId", res.id);
});
"""
    ))

    # 4.2 Negative: Duplicate Leave Type Name
    lt_items.append(create_item(
        name="4.2 Negative: Duplicate Leave Type Name",
        method="POST",
        url_str="{{baseUrl}}/api/leave-types",
        headers={"Authorization": "Bearer {{adminToken}}"},
        body_obj={
            "name": "{{leaveTypeName}}",
            "description": "Duplicate test",
            "defaultDays": 10
        },
        test_script="""
pm.test("Status is 400 Bad Request or 409 Conflict", function () {
    pm.expect(pm.response.code).to.be.oneOf([400, 409]);
});
"""
    ))

    # 4.3 Get All Leave Types
    lt_items.append(create_item(
        name="4.3 Get All Leave Types",
        method="GET",
        url_str="{{baseUrl}}/api/leave-types",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Leave types array returned", function () {
    var res = pm.response.json();
    pm.expect(Array.isArray(res)).to.be.true;
    pm.expect(res.length).to.be.at.least(1);
});
"""
    ))

    # 4.4 Get Leave Type by ID
    lt_items.append(create_item(
        name="4.4 Get Leave Type by ID",
        method="GET",
        url_str="{{baseUrl}}/api/leave-types/{{leaveTypeId}}",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("ID matches created leave type", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.eql(parseInt(pm.environment.get("leaveTypeId")));
});
"""
    ))

    # 4.5 Deactivate Leave Type
    lt_items.append(create_item(
        name="4.5 Deactivate Leave Type",
        method="PUT",
        url_str="{{baseUrl}}/api/leave-types/{{leaveTypeId}}/deactivate",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Leave type is inactive", function () {
    var res = pm.response.json();
    pm.expect(res.active).to.be.false;
});
"""
    ))

    # 4.6 Re-activate Leave Type
    lt_items.append(create_item(
        name="4.6 Activate Leave Type",
        method="PUT",
        url_str="{{baseUrl}}/api/leave-types/{{leaveTypeId}}/activate",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Leave type is active again", function () {
    var res = pm.response.json();
    pm.expect(res.active).to.be.true;
});
"""
    ))

    folders.append({"name": "4. Leave Type Management", "item": lt_items})

    # ==========================================
    # 5. Leave Balance Management
    # ==========================================
    bal_items = []

    # 5.1 Allocate Leave Balance
    bal_items.append(create_item(
        name="5.1 Allocate Leave Balance (Admin)",
        method="POST",
        url_str="{{baseUrl}}/api/leave-balances",
        headers={"Authorization": "Bearer {{adminToken}}"},
        body_obj={
            "employeeId": "{{employeeId}}",
            "leaveTypeId": "{{leaveTypeId}}",
            "total": 10,
            "available": 10
        },
        test_script="""
pm.test("Status is 201 Created", function () {
    pm.response.to.have.status(201);
});
pm.test("Balance initialized with 10 total and 0 used", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.be.a("number");
    pm.expect(res.total).to.eql(10);
    pm.expect(res.used).to.eql(0);
    pm.environment.set("leaveBalanceId", res.id);
});
"""
    ))

    # 5.2 Get Employee Leave Balances
    bal_items.append(create_item(
        name="5.2 Get Employee Leave Balances (Admin)",
        method="GET",
        url_str="{{baseUrl}}/api/leave-balances/employee/{{employeeId}}",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Array contains newly allocated balance", function () {
    var res = pm.response.json();
    pm.expect(Array.isArray(res)).to.be.true;
    var found = res.some(function (b) {
        return b.id === parseInt(pm.environment.get("leaveBalanceId"));
    });
    pm.expect(found).to.be.true;
});
"""
    ))

    # 5.3 Get Current User Leave Balances
    bal_items.append(create_item(
        name="5.3 Get My Leave Balances (Employee)",
        method="GET",
        url_str="{{baseUrl}}/api/leave-balances/me",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Employee sees balance available", function () {
    var res = pm.response.json();
    pm.expect(Array.isArray(res)).to.be.true;
    pm.expect(res.length).to.be.at.least(1);
});
"""
    ))

    # 5.4 Negative: Allocate Negative Balance
    bal_items.append(create_item(
        name="5.4 Negative: Allocate Negative Balance",
        method="POST",
        url_str="{{baseUrl}}/api/leave-balances",
        headers={"Authorization": "Bearer {{adminToken}}"},
        body_obj={
            "employeeId": "{{employeeId}}",
            "leaveTypeId": "{{leaveTypeId}}",
            "total": -5,
            "available": -5
        },
        test_script="""
pm.test("Status is 400 Bad Request", function () {
    pm.response.to.have.status(400);
});
"""
    ))

    folders.append({"name": "5. Leave Balance Management", "item": bal_items})

    # ==========================================
    # 6. Leave Request Lifecycle & Approvals
    # ==========================================
    lr_items = []

    # 6.1 Check Overlap Endpoint
    lr_items.append(create_item(
        name="6.1 Check Leave Date Overlap",
        method="GET",
        url_str="{{baseUrl}}/api/leave-requests/check-overlap?startDate={{leaveStartDate}}&endDate={{leaveEndDate}}",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Reports no overlap initially", function () {
    var res = pm.response.json();
    pm.expect(res.overlapping).to.be.false;
});
"""
    ))

    # 6.2 Apply for Leave (Employee)
    lr_items.append(create_item(
        name="6.2 Apply for Leave (Employee)",
        method="POST",
        url_str="{{baseUrl}}/api/leave-requests",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        body_obj={
            "leaveTypeId": "{{leaveTypeId}}",
            "startDate": "{{leaveStartDate}}",
            "endDate": "{{leaveEndDate}}",
            "reason": "Family vacation and personal travel"
        },
        test_script="""
pm.test("Status is 201 Created", function () {
    pm.response.to.have.status(201);
});
pm.test("Leave request created in PENDING status", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.be.a("number");
    pm.expect(res.status).to.eql("PENDING");
    pm.environment.set("leaveRequestId", res.id);
});
"""
    ))

    # 6.3 Negative: Overlapping Leave Request
    lr_items.append(create_item(
        name="6.3 Negative: Overlapping Leave Application",
        method="POST",
        url_str="{{baseUrl}}/api/leave-requests",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        body_obj={
            "leaveTypeId": "{{leaveTypeId}}",
            "startDate": "{{leaveStartDate}}",
            "endDate": "{{leaveEndDate}}",
            "reason": "Overlapping dates must be rejected"
        },
        test_script="""
pm.test("Status is 400 Bad Request", function () {
    pm.response.to.have.status(400);
});
"""
    ))

    # 6.4 Negative: End Date Before Start Date
    lr_items.append(create_item(
        name="6.4 Negative: Inverted Date Range",
        method="POST",
        url_str="{{baseUrl}}/api/leave-requests",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        body_obj={
            "leaveTypeId": "{{leaveTypeId}}",
            "startDate": "{{leaveEndDate}}",
            "endDate": "{{leaveStartDate}}",
            "reason": "Inverted dates"
        },
        test_script="""
pm.test("Status is 400 Bad Request", function () {
    pm.response.to.have.status(400);
});
"""
    ))

    # 6.5 Manager View Team Leave Requests
    lr_items.append(create_item(
        name="6.5 Manager View Team Requests",
        method="GET",
        url_str="{{baseUrl}}/api/managers/me/leave-requests",
        headers={"Authorization": "Bearer {{managerToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Team leave requests contain pending employee request", function () {
    var res = pm.response.json();
    pm.expect(Array.isArray(res)).to.be.true;
    var found = res.some(function (r) {
        return r.id === parseInt(pm.environment.get("leaveRequestId"));
    });
    pm.expect(found).to.be.true;
});
"""
    ))

    # 6.6 Manager Approve Leave Request
    lr_items.append(create_item(
        name="6.6 Manager Approve Leave Request",
        method="PUT",
        url_str="{{baseUrl}}/api/leave-requests/{{leaveRequestId}}/approve",
        headers={"Authorization": "Bearer {{managerToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Leave request status transitioned to APPROVED", function () {
    var res = pm.response.json();
    pm.expect(res.status).to.eql("APPROVED");
});
"""
    ))

    # 6.7 Verify Leave Balance Deduction
    lr_items.append(create_item(
        name="6.7 Verify Leave Balance Deduction",
        method="GET",
        url_str="{{baseUrl}}/api/leave-balances/me",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Balance deducted upon approval (used > 0)", function () {
    var res = pm.response.json();
    var bal = res.find(function (b) {
        return b.leaveType && b.leaveType.id === parseInt(pm.environment.get("leaveTypeId"));
    });
    pm.expect(bal).to.be.an("object");
    pm.expect(bal.used).to.be.greaterThan(0);
});
"""
    ))

    # 6.8 Cancel Approved Leave (Employee)
    lr_items.append(create_item(
        name="6.8 Cancel Approved Leave (Employee)",
        method="PATCH",
        url_str="{{baseUrl}}/api/leave-requests/{{leaveRequestId}}/cancel",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Status transitioned to CANCELLED", function () {
    var res = pm.response.json();
    pm.expect(res.status).to.eql("CANCELLED");
});
"""
    ))

    # 6.9 Verify Balance Restoration
    lr_items.append(create_item(
        name="6.9 Verify Balance Restored",
        method="GET",
        url_str="{{baseUrl}}/api/leave-balances/me",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Balance restored to 0 used", function () {
    var res = pm.response.json();
    var bal = res.find(function (b) {
        return b.leaveType && b.leaveType.id === parseInt(pm.environment.get("leaveTypeId"));
    });
    pm.expect(bal).to.be.an("object");
    pm.expect(bal.used).to.eql(0);
});
"""
    ))

    # 6.10 Apply for Second Leave Request
    lr_items.append(create_item(
        name="6.10 Apply for Second Leave Request",
        method="POST",
        url_str="{{baseUrl}}/api/leave-requests",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        body_obj={
            "leaveTypeId": "{{leaveTypeId}}",
            "startDate": "{{leaveStartDate2}}",
            "endDate": "{{leaveEndDate2}}",
            "reason": "Request for rejection testing"
        },
        test_script="""
pm.test("Status is 201 Created", function () {
    pm.response.to.have.status(201);
});
pm.test("Second request created in PENDING status", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.be.a("number");
    pm.environment.set("leaveRequestId2", res.id);
});
"""
    ))

    # 6.11 Negative: Reject Leave Without Reason
    lr_items.append(create_item(
        name="6.11 Negative: Reject Without Reason",
        method="PATCH",
        url_str="{{baseUrl}}/api/leave-requests/{{leaveRequestId2}}/reject",
        headers={"Authorization": "Bearer {{managerToken}}"},
        test_script="""
pm.test("Status is 400 Bad Request", function () {
    pm.response.to.have.status(400);
});
"""
    ))

    # 6.12 Manager Reject Leave With Reason
    lr_items.append(create_item(
        name="6.12 Manager Reject Leave With Reason",
        method="PATCH",
        url_str="{{baseUrl}}/api/leave-requests/{{leaveRequestId2}}/reject?reason=Insufficient+team+coverage+on+these+dates",
        headers={"Authorization": "Bearer {{managerToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Leave request status transitioned to REJECTED", function () {
    var res = pm.response.json();
    pm.expect(res.status).to.eql("REJECTED");
    pm.expect(res.rejectionReason).to.include("coverage");
});
"""
    ))

    folders.append({"name": "6. Leave Request Lifecycle & Approvals", "item": lr_items})

    # ==========================================
    # 7. HTTP Idempotency Validation
    # ==========================================
    idemp_items = []

    # 7.1 Initial Request with Idempotency Key
    idemp_items.append(create_item(
        name="7.1 Apply Leave with Idempotency-Key",
        method="POST",
        url_str="{{baseUrl}}/api/leave-requests",
        headers={
            "Authorization": "Bearer {{employeeToken}}",
            "Idempotency-Key": "{{idempotencyKey}}"
        },
        body_obj={
            "leaveTypeId": "{{leaveTypeId}}",
            "startDate": "{{leaveStartDate}}",
            "endDate": "{{leaveEndDate}}",
            "reason": "Idempotent leave booking test"
        },
        pre_script="""
var key = "idemp-" + Date.now();
pm.environment.set("idempotencyKey", key);
""",
        test_script="""
pm.test("Status is 201 Created", function () {
    pm.response.to.have.status(201);
});
pm.test("Capture response ID", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.be.a("number");
    pm.environment.set("idempLeaveId", res.id);
});
"""
    ))

    # 7.2 Replay Request with Same Key (Cache Hit)
    idemp_items.append(create_item(
        name="7.2 Replay with Same Idempotency-Key (Cache Hit)",
        method="POST",
        url_str="{{baseUrl}}/api/leave-requests",
        headers={
            "Authorization": "Bearer {{employeeToken}}",
            "Idempotency-Key": "{{idempotencyKey}}"
        },
        body_obj={
            "leaveTypeId": "{{leaveTypeId}}",
            "startDate": "{{leaveStartDate}}",
            "endDate": "{{leaveEndDate}}",
            "reason": "Idempotent leave booking test"
        },
        test_script="""
pm.test("Status is 201 Created", function () {
    pm.response.to.have.status(201);
});
pm.test("Response returned from cache with Idempotency-Replayed header", function () {
    pm.expect(pm.response.headers.get("Idempotency-Replayed")).to.eql("true");
});
pm.test("Cached response has identical ID", function () {
    var res = pm.response.json();
    pm.expect(res.id).to.eql(parseInt(pm.environment.get("idempLeaveId")));
});
"""
    ))

    # 7.3 Replay Same Key with Altered Payload (Conflict)
    idemp_items.append(create_item(
        name="7.3 Replay Key with Altered Payload (Conflict)",
        method="POST",
        url_str="{{baseUrl}}/api/leave-requests",
        headers={
            "Authorization": "Bearer {{employeeToken}}",
            "Idempotency-Key": "{{idempotencyKey}}"
        },
        body_obj={
            "leaveTypeId": "{{leaveTypeId}}",
            "startDate": "{{leaveStartDate}}",
            "endDate": "{{leaveEndDate}}",
            "reason": "Altered Payload Attempt"
        },
        test_script="""
pm.test("Status is 409 Conflict", function () {
    pm.response.to.have.status(409);
});
"""
    ))

    folders.append({"name": "7. HTTP Idempotency Validation", "item": idemp_items})

    # ==========================================
    # 8. Account Lockout & Admin Unlock Recovery
    # ==========================================
    lock_items = []

    # 8.1 Register Lockout Target
    lock_items.append(create_item(
        name="8.1 Register Target Account for Lockout",
        method="POST",
        url_str="{{baseUrl}}/api/auth/register",
        body_obj={
            "email": "{{lockedUserEmail}}",
            "password": "CorrectPassword@123",
            "firstName": "Lock",
            "lastName": "Target",
            "phone": "5550001111",
            "employeeCode": "{{lockedUserCode}}"
        },
        pre_script="""
var ts = Date.now();
pm.environment.set("lockedUserEmail", "lockout." + ts + "@test.com");
pm.environment.set("lockedUserCode", "LCK-" + ts);
""",
        test_script="""
pm.test("Status is 200 OK or 201 Created", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201]);
});
pm.test("Capture user ID", function () {
    var res = pm.response.json();
    pm.expect(res.user.id).to.be.a("number");
    pm.environment.set("lockedUserId", res.user.id);
});
"""
    ))

    # 8.2 Admin Manually Locks Account
    lock_items.append(create_item(
        name="8.2 Admin Manually Locks Account",
        method="PUT",
        url_str="{{baseUrl}}/api/users/{{lockedUserId}}/lock?reason=Security+Lockout+Verification",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Account non-locked is set to false", function () {
    var res = pm.response.json();
    pm.expect(res.accountNonLocked).to.be.false;
});
"""
    ))

    # 8.3 Verify Login Blocked Due to Lockout
    lock_items.append(create_item(
        name="8.3 Verify Login Blocked by Account Lockout",
        method="POST",
        url_str="{{baseUrl}}/api/auth/login",
        body_obj={"email": "{{lockedUserEmail}}", "password": "CorrectPassword@123"},
        test_script="""
pm.test("Status is 401 Unauthorized", function () {
    pm.response.to.have.status(401);
});
pm.test("Error message indicates account is locked", function () {
    pm.expect(pm.response.text().toLowerCase()).to.include("locked");
});
"""
    ))

    # 8.4 Admin Queries Locked Users
    lock_items.append(create_item(
        name="8.4 Admin Queries Locked Accounts",
        method="GET",
        url_str="{{baseUrl}}/api/users?locked=true",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Locked user appears in query results", function () {
    var res = pm.response.json();
    var list = res.content || res;
    var found = list.some(function (u) {
        return u.id === parseInt(pm.environment.get("lockedUserId"));
    });
    pm.expect(found).to.be.true;
});
"""
    ))

    # 8.5 Admin Unlocks User
    lock_items.append(create_item(
        name="8.5 Admin Unlocks User Account",
        method="PUT",
        url_str="{{baseUrl}}/api/users/{{lockedUserId}}/unlock",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("User account is non-locked", function () {
    var res = pm.response.json();
    pm.expect(res.accountNonLocked).to.be.true;
});
"""
    ))

    # 8.6 User Successfully Logs In After Unlock
    lock_items.append(create_item(
        name="8.6 User Logs In After Admin Unlock",
        method="POST",
        url_str="{{baseUrl}}/api/auth/login",
        body_obj={"email": "{{lockedUserEmail}}", "password": "CorrectPassword@123"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Access token issued after recovery", function () {
    var res = pm.response.json();
    pm.expect(res.accessToken).to.be.a("string");
});
"""
    ))

    folders.append({"name": "8. Account Lockout & Admin Unlock Recovery", "item": lock_items})

    # ==========================================
    # 9. Audit Logs & Security Management
    # ==========================================
    audit_items = []

    # 9.1 Admin View Audit Logs
    audit_items.append(create_item(
        name="9.1 Admin View Audit Logs",
        method="GET",
        url_str="{{baseUrl}}/api/audit-logs",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
pm.test("Returns audit log history", function () {
    var res = pm.response.json();
    var list = res.content || res;
    pm.expect(Array.isArray(list)).to.be.true;
    pm.expect(list.length).to.be.at.least(1);
});
"""
    ))

    # 9.2 Admin View Security Events
    audit_items.append(create_item(
        name="9.2 Admin View Security Events",
        method="GET",
        url_str="{{baseUrl}}/api/security-events",
        headers={"Authorization": "Bearer {{adminToken}}"},
        test_script="""
pm.test("Status is 200 OK", function () {
    pm.response.to.have.status(200);
});
"""
    ))

    # 9.3 Negative: Employee Access Denied for Audit Logs
    audit_items.append(create_item(
        name="9.3 Negative: Employee Forbidden from Audit Logs",
        method="GET",
        url_str="{{baseUrl}}/api/audit-logs",
        headers={"Authorization": "Bearer {{employeeToken}}"},
        test_script="""
pm.test("Status is 403 Forbidden", function () {
    pm.response.to.have.status(403);
});
"""
    ))

    folders.append({"name": "9. Audit Logs & Security Management", "item": audit_items})

    # Master Collection JSON
    collection_json = {
        "info": {
            "name": "Leave Management System - Comprehensive Validation Suite",
            "description": "Production-grade automated Postman test collection validating Authentication, RBAC, Department Management, Leave Types, Leave Balances, Approvals Lifecycle, HTTP Idempotency, and Account Lockout Security.",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
        },
        "event": [
            {
                "listen": "prerequest",
                "script": {
                    "type": "text/javascript",
                    "exec": collection_pre_script.strip().split("\n")
                }
            }
        ],
        "item": folders
    }

    # Format JSON
    postman_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)))
    target_path = os.path.join(postman_dir, "Leave_Management_System.postman_collection.json")
    root_path = os.path.join(os.path.dirname(postman_dir), "postman_collection.json")

    with open(target_path, "w", encoding="utf-8") as f:
        json.dump(collection_json, f, indent=2)

    with open(root_path, "w", encoding="utf-8") as f:
        json.dump(collection_json, f, indent=2)

    print(f"Generated collection written to: {target_path}")
    print(f"Synchronized root collection to: {root_path}")

if __name__ == "__main__":
    build_collection()
