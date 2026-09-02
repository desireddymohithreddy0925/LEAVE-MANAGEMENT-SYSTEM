#!/bin/bash

BASE_URL="http://localhost:8081/api"

echo "--- 1. Create Department ---"
curl -s -X POST "$BASE_URL/departments" -H "Content-Type: application/json" -d '{"name": "Engineering", "description": "Eng Dept"}' | jq .

echo "\n--- 2. Create Employee ---"
curl -s -X POST "$BASE_URL/employees" -H "Content-Type: application/json" -d '{"firstName": "John", "lastName": "Doe", "email": "john@example.com", "phone": "12345", "departmentId": 1}' | jq .

echo "\n--- 3. Create Leave Type ---"
curl -s -X POST "$BASE_URL/leave-types" -H "Content-Type: application/json" -d '{"name": "Annual Leave", "description": "Yearly leave", "defaultDays": 20}' | jq .

echo "\n--- 4. Assign Leave Balance ---"
curl -s -X POST "$BASE_URL/leave-balances" -H "Content-Type: application/json" -d '{"employeeId": 1, "leaveTypeId": 1, "availableDays": 20}' | jq .

echo "\n--- 5. Apply for Leave (Pending) ---"
START_DATE=$(date -v +5d +%Y-%m-%d)
END_DATE=$(date -v +10d +%Y-%m-%d)
curl -s -X POST "$BASE_URL/leave-requests" -H "Content-Type: application/json" -d "{\"employeeId\": 1, \"leaveTypeId\": 1, \"startDate\": \"$START_DATE\", \"endDate\": \"$END_DATE\", \"reason\": \"Vacation\"}" | jq .

echo "\n--- 6. Approve Leave ---"
curl -s -X PUT "$BASE_URL/leave-requests/1/approve" | jq .

echo "\n--- 7. Verify Balance Deduction ---"
curl -s -X GET "$BASE_URL/leave-balances/employee/1" | jq .

echo "\n--- 8. Attempt Overlapping Leave (Expect Failure) ---"
OVERLAP_END=$(date -v +6d +%Y-%m-%d)
curl -s -w "\nHTTP STATUS: %{http_code}\n" -X POST "$BASE_URL/leave-requests" -H "Content-Type: application/json" -d "{\"employeeId\": 1, \"leaveTypeId\": 1, \"startDate\": \"$START_DATE\", \"endDate\": \"$OVERLAP_END\", \"reason\": \"Overlap\"}" | jq .

echo "\n--- 9. Apply for Another Leave ---"
NEW_START=$(date -v +20d +%Y-%m-%d)
NEW_END=$(date -v +22d +%Y-%m-%d)
curl -s -X POST "$BASE_URL/leave-requests" -H "Content-Type: application/json" -d "{\"employeeId\": 1, \"leaveTypeId\": 1, \"startDate\": \"$NEW_START\", \"endDate\": \"$NEW_END\", \"reason\": \"Another Vacation\"}" | jq .

echo "\n--- 10. Reject Leave without Reason (Expect Failure) ---"
curl -s -w "\nHTTP STATUS: %{http_code}\n" -X PUT "$BASE_URL/leave-requests/2/reject" | jq .

echo "\n--- 11. Reject Leave with Reason (Expect Success) ---"
curl -s -X PUT "$BASE_URL/leave-requests/2/reject?reason=Not%20enough%20staff" | jq .

echo "\n--- 12. Cancel Approved Leave ---"
curl -s -X PUT "$BASE_URL/leave-requests/1/cancel" | jq .

echo "\n--- 13. Verify Balance Restoration ---"
curl -s -X GET "$BASE_URL/leave-balances/employee/1" | jq .
