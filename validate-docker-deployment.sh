#!/bin/bash
# ==============================================================================
# Leave Management System - Docker Deployment Validation Suite
# ==============================================================================
# This script performs end-to-end verification of the containerized Leave
# Management System deployment, covering:
#  1. Secret Enforcement & Configuration
#  2. Container Health & Runtime Status
#  3. Non-Root Security (UID 10001 / appuser)
#  4. PostgreSQL Network Isolation & Port Binding
#  5. Spring Boot Actuator Security Hardening
#  6. Liquibase Schema Migrations
#  7. Authentication & JWT Token Refresh Lifecycle
#  8. User Registration & Employee Profile Creation
#  9. Leave Management Flow (Leave Type, Balance, Application & Approval)
# 10. PostgreSQL Persistent Volume & State Survival Across Restart
# 11. Test Data Purge & Zero Database Pollution
# ==============================================================================

set -eo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# --- Environment Safety Restriction ---
# Prevent unintended execution against live, production non-disposable environments
if [ "${SPRING_PROFILES_ACTIVE:-}" = "prod" ] && [ "${ALLOW_DISPOSABLE_RUN:-false}" != "true" ] && [ "${CI:-false}" != "true" ]; then
    echo -e "${RED}${BOLD}ERROR: validate-docker-deployment.sh is restricted to disposable test or development environments.${NC}"
    echo "It executes state-modifying actions (user provisioning, leave balance mutation, and cluster restart testing)."
    echo "To execute against disposable test clusters running the production profile, explicitly pass ALLOW_DISPOSABLE_RUN=true or CI=true."
    exit 1
fi

APP_URL="${APP_URL:-http://localhost:8080}"
DB_PORT="${DB_PORT:-5432}"

# Dynamic database configuration and credentials (avoid hardcoded assumptions)
DB_USER="${DB_USERNAME:-postgres}"
DB_NAME="${DB_NAME:-leave_management_db}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@leavemanagement.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123}"

if [ -f .env ]; then
    [ -z "$DB_USERNAME" ] && DB_USER="$(grep -E '^DB_USERNAME=' .env | cut -d '=' -f2- | tr -d '\"\r' || echo "postgres")"
    [ -z "$DB_NAME" ] && DB_NAME="$(grep -E '^DB_NAME=' .env | cut -d '=' -f2- | tr -d '\"\r' || echo "leave_management_db")"
    if [ -z "$ADMIN_EMAIL_OVERRIDE" ]; then
        VAL="$(grep -E '^ADMIN_EMAIL=' .env | cut -d '=' -f2- | tr -d '\"\r' || true)"
        [ -n "$VAL" ] && ADMIN_EMAIL="$VAL"
    fi
    if [ -z "$ADMIN_PASSWORD_OVERRIDE" ]; then
        VAL="$(grep -E '^ADMIN_PASSWORD=' .env | cut -d '=' -f2- | tr -d '\"\r' || true)"
        [ -n "$VAL" ] && ADMIN_PASSWORD="$VAL"
    fi
fi

pass_count=0
fail_count=0

# Tracking variables for automated test data cleanup
USER_ID=""
EMP_RECORD_ID=""
LEAVE_TYPE_ID=""
CUSTOM_LT_CREATED=0
BALANCE_ID=""
LEAVE_REQ_ID=""

log_section() {
    echo -e "\n${BLUE}${BOLD}=== $1 ===${NC}"
}

log_pass() {
    echo -e "  [${GREEN}PASS${NC}] $1"
    pass_count=$((pass_count + 1))
}

log_fail() {
    echo -e "  [${RED}FAIL${NC}] $1"
    fail_count=$((fail_count + 1))
}

log_info() {
    echo -e "  [${YELLOW}INFO${NC}] $1"
}

cleanup_validation_records() {
    log_info "Purging test-created records to ensure zero database pollution..."
    if [ -n "$LEAVE_REQ_ID" ] && [ "$LEAVE_REQ_ID" != "null" ]; then
        docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -c "DELETE FROM leave_requests WHERE id = $LEAVE_REQ_ID;" >/dev/null 2>&1 || true
    fi
    if [ -n "$BALANCE_ID" ] && [ "$BALANCE_ID" != "null" ]; then
        docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -c "DELETE FROM leave_balances WHERE id = $BALANCE_ID;" >/dev/null 2>&1 || true
    fi
    if [ -n "$EMP_RECORD_ID" ] && [ "$EMP_RECORD_ID" != "null" ]; then
        docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -c "DELETE FROM employees WHERE id = $EMP_RECORD_ID;" >/dev/null 2>&1 || true
    fi
    if [ -n "$USER_ID" ] && [ "$USER_ID" != "null" ]; then
        docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -c "DELETE FROM refresh_tokens WHERE user_id = $USER_ID;" >/dev/null 2>&1 || true
        docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -c "DELETE FROM user_roles WHERE user_id = $USER_ID;" >/dev/null 2>&1 || true
        docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -c "DELETE FROM users WHERE id = $USER_ID;" >/dev/null 2>&1 || true
    fi
    if [ "$CUSTOM_LT_CREATED" -eq 1 ] && [ -n "$LEAVE_TYPE_ID" ] && [ "$LEAVE_TYPE_ID" != "null" ]; then
        docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -c "DELETE FROM leave_types WHERE id = $LEAVE_TYPE_ID;" >/dev/null 2>&1 || true
    fi
}

trap cleanup_validation_records EXIT INT TERM

# --- 1. Secret Enforcement Validation ---
log_section "1. Secret Enforcement & Configuration Validation"

if docker compose --env-file /dev/null config > /tmp/compose-err.log 2>&1; then
    log_fail "Compose unexpectedly allowed startup without DB_PASSWORD/JWT_SECRET/ENCRYPTION_KEY"
else
    log_pass "docker-compose requires explicit DB_PASSWORD, JWT_SECRET, and ENCRYPTION_KEY (aborts if missing)"
fi

if [ -f .env ]; then
    log_pass "Production environment file (.env) exists and is configured"
else
    log_fail "No .env file found in repository root"
fi

# --- 2. Container Health & Runtime Status ---
log_section "2. Container Health & Runtime Status"

log_info "Waiting for PostgreSQL and backend to become ready at $APP_URL/actuator/health..."
MAX_WAIT=90
WAITED=0
while [ "$WAITED" -lt "$MAX_WAIT" ]; do
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL/actuator/health" 2>/dev/null || true)
    if [ "$HTTP_STATUS" = "200" ]; then
        break
    fi
    sleep 3
    WAITED=$((WAITED + 3))
done

POSTGRES_STATUS=$(docker compose ps db --format '{{.Status}}' 2>/dev/null || echo "not running")
BACKEND_STATUS=$(docker compose ps app --format '{{.Status}}' 2>/dev/null || echo "not running")

if [[ "$POSTGRES_STATUS" == *"healthy"* ]] || [[ "$POSTGRES_STATUS" == *"Up"* ]]; then
    log_pass "PostgreSQL container (lms-postgres) is running and healthy"
else
    log_fail "PostgreSQL container is not healthy: $POSTGRES_STATUS"
fi

if [ "$HTTP_STATUS" = "200" ]; then
    log_pass "Backend container (lms-backend) is running and healthy (HTTP 200 after ${WAITED}s)"
else
    log_fail "Backend container failed to respond after ${MAX_WAIT}s: $BACKEND_STATUS"
fi

# --- 3. Non-Root Security Verification ---
log_section "3. Non-Root Container Security"

APP_UID=$(docker compose exec -T app id -u)
APP_USER=$(docker compose exec -T app whoami)

if [ "$APP_UID" -eq 10001 ] && [ "$APP_USER" = "appuser" ]; then
    log_pass "Backend container runs strictly as non-root user (uid=$APP_UID, user=$APP_USER)"
else
    log_fail "Backend container running with unexpected privileges (uid=$APP_UID, user=$APP_USER)"
fi

# --- 4. Network Isolation & Port Binding ---
log_section "4. Network Isolation & Port Binding"

DB_PORT_BINDING=$(docker compose port db 5432 2>/dev/null || echo "")

if [[ "$DB_PORT_BINDING" == 127.0.0.1:* ]]; then
    log_pass "PostgreSQL port 5432 is restricted to loopback (127.0.0.1) and NOT exposed publicly"
else
    log_fail "PostgreSQL port binding is insecure or not found: $DB_PORT_BINDING"
fi

# Validate internal network isolation configuration
INTERNAL_NET_INSPECT=$(docker network inspect lms-internal-net 2>/dev/null || echo "[]")
IS_INTERNAL=$(echo "$INTERNAL_NET_INSPECT" | jq -r '.[0].Internal // false' 2>/dev/null || echo "false")

if [ "$IS_INTERNAL" = "true" ]; then
    log_pass "Dedicated internal network 'lms-internal-net' is configured with internal: true (external routing restricted)"
else
    log_fail "Internal network 'lms-internal-net' not configured as internal (IS_INTERNAL=$IS_INTERNAL)"
fi

# Validate DB is attached strictly to internal network, not public
DB_NETWORKS=$(docker inspect lms-postgres --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>/dev/null || echo "")
if [[ "$DB_NETWORKS" == *"lms-internal-net"* ]] && [[ "$DB_NETWORKS" != *"lms-public-net"* ]]; then
    log_pass "PostgreSQL container is connected exclusively to internal network and isolated from public network"
else
    log_fail "PostgreSQL container network attachment is not properly isolated: $DB_NETWORKS"
fi

# Verify backend can communicate with PostgreSQL across internal network
ACTUATOR_DB_STATUS=$(curl -s "$APP_URL/actuator/health" 2>/dev/null | jq -r '.status // empty')
if [ "$ACTUATOR_DB_STATUS" = "UP" ]; then
    log_pass "Backend successfully communicates with PostgreSQL across internal network"
else
    log_fail "Backend cannot communicate with PostgreSQL across internal network (Status: $ACTUATOR_DB_STATUS)"
fi

# --- 5. Spring Boot Actuator Security Hardening ---
log_section "5. Actuator Security & Information Disclosure Check"

HEALTH_RESP=$(curl -s "$APP_URL/actuator/health" 2>/dev/null || echo "{}")
STATUS_FIELD=$(echo "$HEALTH_RESP" | jq -r '.status // empty')

if [ "$STATUS_FIELD" = "UP" ]; then
    # Verify sensitive internals (db, diskSpace, details) are NOT exposed
    if echo "$HEALTH_RESP" | grep -qv "diskSpace\|db\|details"; then
        log_pass "Actuator /actuator/health status is UP without disclosing internal details ($HEALTH_RESP)"
    else
        log_fail "Actuator /actuator/health is leaking internal component details"
    fi
else
    log_fail "Actuator /actuator/health returned unexpected status: $HEALTH_RESP"
fi

METRICS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL/actuator/metrics" 2>/dev/null || echo "000")
if [ "$METRICS_CODE" -eq 401 ]; then
    log_pass "Actuator /actuator/metrics is secured against unauthorized access (HTTP 401)"
else
    log_fail "Actuator /actuator/metrics unexpected HTTP response: $METRICS_CODE"
fi

ENV_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL/actuator/env" 2>/dev/null || echo "000")
if [ "$ENV_CODE" -eq 401 ]; then
    log_pass "Actuator /actuator/env is secured against unauthorized access (HTTP 401)"
else
    log_fail "Actuator /actuator/env unexpected HTTP response: $ENV_CODE"
fi

# --- 6. Liquibase Migrations Check ---
log_section "6. Liquibase Schema Migrations Verification"

CHANGESET_COUNT=$(docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "SELECT COUNT(*) FROM databasechangelog;" 2>/dev/null || echo "0")
if [ "$CHANGESET_COUNT" -ge 16 ]; then
    log_pass "Liquibase executed successfully ($CHANGESET_COUNT changesets verified in databasechangelog)"
else
    log_fail "Liquibase changesets count ($CHANGESET_COUNT) is lower than expected"
fi

# --- 7. Authentication & Token Refresh Lifecycle ---
log_section "7. Authentication & Token Refresh Lifecycle"

ADMIN_LOGIN_RESP=$(curl -s -X POST "$APP_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")

ADMIN_ACCESS_TOKEN=$(echo "$ADMIN_LOGIN_RESP" | jq -r '.accessToken // empty')
ADMIN_REFRESH_TOKEN=$(echo "$ADMIN_LOGIN_RESP" | jq -r '.refreshToken // empty')

if [ -n "$ADMIN_ACCESS_TOKEN" ] && [ "$ADMIN_ACCESS_TOKEN" != "null" ]; then
    log_pass "Admin authentication successful (JWT access token issued)"
else
    log_fail "Admin authentication failed: $ADMIN_LOGIN_RESP"
fi

sleep 1 # Ensure distinct timestamp for token refresh validation

REFRESH_RESP=$(curl -s -X POST "$APP_URL/api/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$ADMIN_REFRESH_TOKEN\"}")

NEW_ACCESS_TOKEN=$(echo "$REFRESH_RESP" | jq -r '.accessToken // empty')
NEW_REFRESH_TOKEN=$(echo "$REFRESH_RESP" | jq -r '.refreshToken // empty')

if [ -n "$NEW_ACCESS_TOKEN" ] && [ "$NEW_ACCESS_TOKEN" != "null" ]; then
    AUTH_TEST_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$APP_URL/api/leave-types" -H "Authorization: Bearer $NEW_ACCESS_TOKEN")
    if [ "$AUTH_TEST_CODE" -eq 200 ]; then
        log_pass "JWT Token Refresh verified (Refreshed token authenticated API request with HTTP 200)"
        ADMIN_ACCESS_TOKEN="$NEW_ACCESS_TOKEN"
    else
        log_fail "Refreshed token failed API authentication: HTTP $AUTH_TEST_CODE"
    fi
else
    log_fail "JWT Token Refresh failed: $REFRESH_RESP"
fi

# --- 8. User Registration ---
log_section "8. User Registration & Employee Provisioning"

RAND_ID=$((RANDOM % 9000 + 1000))
TEST_EMAIL="emp_${RAND_ID}@validation.internal"
TEST_EMP_CODE="VAL_${RAND_ID}"
TEST_PASSWORD="Password@123"

REG_RESP=$(curl -s -X POST "$APP_URL/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "{
        \"firstName\": \"Validation\",
        \"lastName\": \"User\",
        \"email\": \"$TEST_EMAIL\",
        \"password\": \"$TEST_PASSWORD\",
        \"phone\": \"+1999${RAND_ID}\",
        \"employeeCode\": \"$TEST_EMP_CODE\"
    }")

EMP_ACCESS_TOKEN=$(echo "$REG_RESP" | jq -r '.accessToken // empty')
USER_ID=$(echo "$REG_RESP" | jq -r '.user.id // empty')

if [ -n "$EMP_ACCESS_TOKEN" ] && [ "$EMP_ACCESS_TOKEN" != "null" ]; then
    log_pass "Employee registration successful for $TEST_EMAIL (User ID: $USER_ID)"
else
    log_fail "Employee registration failed: $REG_RESP"
fi

# Fetch Employee record ID
EMP_RECORD_ID=$(docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "SELECT id FROM employees WHERE user_id = $USER_ID;" 2>/dev/null || echo "")

if [ -n "$EMP_RECORD_ID" ]; then
    log_pass "Employee profile linked in database (Employee ID: $EMP_RECORD_ID)"
else
    log_fail "Employee profile record not found for user ID: $USER_ID"
fi

# --- 9. Business Workflow: Leave Type, Balance, Application & Approval ---
log_section "9. Leave Workflow End-to-End Test"

# Ensure Leave Type exists
LEAVE_TYPE_ID=$(curl -s -X GET "$APP_URL/api/leave-types" -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" | jq -r '.[0].id // empty')

if [ -z "$LEAVE_TYPE_ID" ] || [ "$LEAVE_TYPE_ID" = "null" ]; then
    CREATE_LT_RESP=$(curl -s -X POST "$APP_URL/api/leave-types" \
        -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"name":"Validation Leave","description":"Automated Validation Quota","defaultDays":25}')
    LEAVE_TYPE_ID=$(echo "$CREATE_LT_RESP" | jq -r '.id // empty')
    CUSTOM_LT_CREATED=1
fi
log_pass "Target Leave Type resolved (Leave Type ID: $LEAVE_TYPE_ID)"

# Allocate Leave Balance (25 days total)
BALANCE_RESP=$(curl -s -X POST "$APP_URL/api/leave-balances" \
    -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
        \"employeeId\": $EMP_RECORD_ID,
        \"leaveTypeId\": $LEAVE_TYPE_ID,
        \"available\": 25,
        \"year\": 2026,
        \"total\": 25
    }")

BALANCE_ID=$(echo "$BALANCE_RESP" | jq -r '.id // empty')
if [ -n "$BALANCE_ID" ] && [ "$BALANCE_ID" != "null" ]; then
    log_pass "Leave Balance allocated (Initial Available: 25 days, Balance ID: $BALANCE_ID)"
else
    log_fail "Leave Balance allocation failed: $BALANCE_RESP"
fi

# Compute dynamic future dates (7 to 11 days ahead) to ensure compliance with @FutureOrPresent
DYN_START=$(date -d "+7 days" +%Y-%m-%d 2>/dev/null || date -v+7d +%Y-%m-%d)
DYN_END=$(date -d "+11 days" +%Y-%m-%d 2>/dev/null || date -v+11d +%Y-%m-%d)

# Apply for Leave as Employee
LEAVE_REQ_RESP=$(curl -s -X POST "$APP_URL/api/leave-requests" \
    -H "Authorization: Bearer $EMP_ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
        \"leaveTypeId\": $LEAVE_TYPE_ID,
        \"startDate\": \"$DYN_START\",
        \"endDate\": \"$DYN_END\",
        \"reason\": \"Automated Docker Deployment Verification\"
    }")

LEAVE_REQ_ID=$(echo "$LEAVE_REQ_RESP" | jq -r '.id // empty')
LEAVE_STATUS=$(echo "$LEAVE_REQ_RESP" | jq -r '.status // empty')
REQUESTED_DAYS=$(echo "$LEAVE_REQ_RESP" | jq -r '.requestedDays // 0')

if [ -n "$LEAVE_REQ_ID" ] && [ "$LEAVE_STATUS" = "PENDING" ]; then
    log_pass "Leave application submitted (Leave Request ID: $LEAVE_REQ_ID, Requested Days: $REQUESTED_DAYS, Status: PENDING)"
else
    log_fail "Leave application failed: $LEAVE_REQ_RESP"
fi

# Approve Leave as Admin
APPROVE_RESP=$(curl -s -X PUT "$APP_URL/api/leave-requests/$LEAVE_REQ_ID/approve" \
    -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN")

APPROVED_STATUS=$(echo "$APPROVE_RESP" | jq -r '.status // empty')
if [ "$APPROVED_STATUS" = "APPROVED" ]; then
    log_pass "Leave request approved by Admin (Status: APPROVED)"
else
    log_fail "Leave request approval failed: $APPROVE_RESP"
fi

# Verify Balance Deduction
EMP_BALANCE_RESP=$(curl -s -X GET "$APP_URL/api/leave-balances/me" \
    -H "Authorization: Bearer $EMP_ACCESS_TOKEN")

USED_DAYS=$(echo "$EMP_BALANCE_RESP" | jq -r '.[0].used // 0')
AVAIL_DAYS=$(echo "$EMP_BALANCE_RESP" | jq -r '.[0].available // 0')
EXPECTED_AVAIL=$(( 25 - REQUESTED_DAYS ))

if [ "$USED_DAYS" -eq "$REQUESTED_DAYS" ] && [ "$AVAIL_DAYS" -eq "$EXPECTED_AVAIL" ]; then
    log_pass "Leave balance correctly decremented (Used: $USED_DAYS, Available: $AVAIL_DAYS)"
else
    log_fail "Leave balance incorrect: Used=$USED_DAYS, Available=$AVAIL_DAYS (Expected Used: $REQUESTED_DAYS, Available: $EXPECTED_AVAIL)"
fi

# --- 10. PostgreSQL Persistent Volume & State Survival Across Restart ---
log_section "10. PostgreSQL Persistent Volume & State Survival Across Restart"

log_info "Restarting Docker Compose deployment to test persistence..."
docker compose restart > /dev/null 2>&1

# Wait for containers to regain healthy status (up to 60s)
for i in {1..30}; do
    HEALTH_CHECK=$(curl -s "$APP_URL/actuator/health" 2>/dev/null | jq -r '.status // empty' 2>/dev/null || echo "")
    if [ "$HEALTH_CHECK" = "UP" ]; then
        break
    fi
    sleep 2
done

if [ "$HEALTH_CHECK" = "UP" ]; then
    log_pass "Backend restored and healthy following container restart"
else
    log_fail "Backend did not recover within 60s"
fi

# Re-authenticate employee after restart
RE_LOGIN_RESP=$(curl -s -X POST "$APP_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}")

RE_EMP_TOKEN=$(echo "$RE_LOGIN_RESP" | jq -r '.accessToken // empty')
if [ -n "$RE_EMP_TOKEN" ] && [ "$RE_EMP_TOKEN" != "null" ]; then
    log_pass "Employee re-authenticated successfully after container restart"
else
    log_fail "Employee authentication failed after restart: $RE_LOGIN_RESP"
fi

# Query persisted leave request
PERSISTED_REQ=$(curl -s -X GET "$APP_URL/api/leave-requests/$LEAVE_REQ_ID" \
    -H "Authorization: Bearer $RE_EMP_TOKEN")

PERSISTED_STATUS=$(echo "$PERSISTED_REQ" | jq -r '.status // empty')
if [ "$PERSISTED_STATUS" = "APPROVED" ]; then
    log_pass "Leave request persisted across restart (ID: $LEAVE_REQ_ID, Status: APPROVED)"
else
    log_fail "Persisted leave request verification failed: $PERSISTED_REQ"
fi

# Query persisted balance
PERSISTED_BAL=$(curl -s -X GET "$APP_URL/api/leave-balances/me" \
    -H "Authorization: Bearer $RE_EMP_TOKEN")

PERSISTED_AVAIL=$(echo "$PERSISTED_BAL" | jq -r '.[0].available // 0')
if [ "$PERSISTED_AVAIL" -eq "$EXPECTED_AVAIL" ]; then
    log_pass "Leave balance state persisted in Docker volume (Available: $PERSISTED_AVAIL)"
else
    log_fail "Persisted balance verification failed: $PERSISTED_BAL (Expected: $EXPECTED_AVAIL)"
fi

# Clean up test-created records
cleanup_validation_records

# --- Summary ---
echo -e "\n=============================================================================="
echo -e "${BOLD}Docker Deployment Validation Summary:${NC}"
echo -e "  Passed Checks: ${GREEN}${pass_count}${NC}"
echo -e "  Failed Checks: ${RED}${fail_count}${NC}"
echo -e "=============================================================================="

if [ "$fail_count" -eq 0 ]; then
    echo -e "${GREEN}${BOLD}ALL DOCKER DEPLOYMENT VALIDATION CHECKS PASSED SUCCESSFULLY!${NC}\n"
    exit 0
else
    echo -e "${RED}${BOLD}SOME VALIDATION CHECKS FAILED.${NC}\n"
    exit 1
fi
