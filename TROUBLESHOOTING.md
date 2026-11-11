# Enterprise Troubleshooting & Incident Playbook
## Leave Management System (LMS)

This operational runbook provides diagnostic workflows, root-cause analyses, and recovery procedures for production issues encountered in the Leave Management System.

---

## 1. Incident Severity Matrix & Triage Workflow

```
                   INCIDENT DETECTED (Alert / User Report)
                                      │
                                      ▼
             ┌──────────────────────────────────────────────────┐
             │       Evaluate Impact & Classify Severity        │
             └────────────────────────┬─────────────────────────┘
                                      │
         ┌───────────────┬────────────┴───────────┬───────────────┐
         ▼               ▼                        ▼               ▼
     [P1: BLOCKER]  [P2: CRITICAL]          [P3: MAJOR]     [P4: MINOR]
   System / DB Out   High Error / SLA Fail   Feature Degraded  Cosmetic / Warning
         │               │                        │               │
         ▼               ▼                        ▼               ▼
   Page On-Call    15-min SLA Triage        1-hour Response  Next Business Day
```

| Severity | Definition | SLA Target | Escalation Path |
|----------|------------|------------|-----------------|
| **P1 - Blocker** | Entire API unavailable, database down, connection pool deadlock, or total data corruption. | MTTA: < 5 min<br>MTTR: < 30 min | Immediate on-call page, bridge opened |
| **P2 - Critical** | High error rates (>5%), p95 latency > 1000ms, idempotency replay failure, or balance calculation anomalies. | MTTA: < 15 min<br>MTTR: < 2 hours | Lead Backend Engineer & DBA notified |
| **P3 - Major** | Single non-critical endpoint failing (e.g. export reports), individual user account locked, or transient timeouts. | MTTA: < 1 hour<br>MTTR: < 8 hours | Normal ticket triage |
| **P4 - Minor** | Minor UI mismatch, non-blocking log warnings, or cosmetic metric label adjustments. | MTTA: < 24 hours<br>MTTR: Next sprint | Product backlog |

---

## 2. Playbook 1: HikariCP Connection Pool Exhaustion & Thread Starvation

### Symptoms
- Application throws:
  ```text
  java.sql.SQLTransientConnectionException: LMS-HikariPool - Connection is not available, request timed out after 20000ms.
  ```
- HTTP `500 Internal Server Error` responses on database-intensive endpoints.
- Diagnostic endpoint `/api/audit/performance/pool-status` returns `"poolStatus": "CRITICAL"` with `threadsAwaitingConnection > 5`.

### Root Causes
1. **Slow Queries or Table Scans**: Transactions holding connections open while scanning large unindexed tables.
2. **Blocking Operations in `@Transactional`**: Making remote HTTP or disk I/O calls inside a database transaction.
3. **Connection Leaks**: Code paths opening raw JDBC connections or long-running entity graph traversals without closing streams.
4. **Under-provisioned Pool Size**: Concurrency exceeds `maximum-pool-size` (default 20).

### Diagnostic Steps
1. Inspect live connection pool status:
   ```bash
   curl -s -H "Authorization: Bearer <ADMIN_TOKEN>" http://localhost:8080/api/audit/performance/pool-status | jq .
   ```
2. Search application logs for Hikari leak detection warnings:
   ```bash
   docker logs lms-backend 2>&1 | grep -i "Apparent connection leak detected"
   ```
3. Inspect active database server connections in PostgreSQL:
   ```sql
   SELECT pid, now() - query_start AS duration, query, state
   FROM pg_stat_activity
   WHERE state != 'idle'
   ORDER BY duration DESC;
   ```

### Remediation
- **Immediate Mitigation**: If connections are stuck on long queries, terminate blocking queries:
  ```sql
  SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE state != 'idle' AND now() - query_start > interval '30 seconds';
  ```
- **Pool Sizing Tuning**: In `docker-compose.yml` or `.env`, increase `maximum-pool-size` to match server CPU cores ($N_{\text{cores}} \times 2 + 2$):
  ```yaml
  spring.datasource.hikari.maximum-pool-size: 30
  spring.datasource.hikari.connection-timeout: 30000
  ```
- **Code Fix**: Move third-party API or email calls outside `@Transactional` service methods.

---

## 3. Playbook 2: Database Deadlocks & Lock Wait Timeouts

### Symptoms
- Error logged:
  ```text
  org.springframework.dao.CannotAcquireLockException: could not execute statement [ERROR: deadlock detected]
  org.springframework.dao.PessimisticLockingFailureException: Lock wait timeout exceeded
  ```
- Concurrent leave approval or balance deduction requests fail with HTTP 500 or 409.

### Root Causes
- Out-of-order resource acquisition between competing transactions (e.g. Transaction A locks `LeaveBalance` then `Employee`, while Transaction B locks `Employee` then `LeaveBalance`).

### Diagnostic Steps
1. Check PostgreSQL deadlock logs:
   ```bash
   docker logs lms-postgres 2>&1 | grep -E "deadlock detected|DETAIL: Process"
   ```
2. Check unindexed foreign keys causing full table row-locks:
   ```bash
   curl -s -H "Authorization: Bearer <ADMIN_TOKEN>" http://localhost:8080/api/audit/performance/query-diagnostics | jq .
   ```

### Remediation
- Ensure all business services follow the LMS canonical locking order:
  $$\text{Employee} \longrightarrow \text{LeaveBalance} \longrightarrow \text{LeaveRequest}$$
- Verify that queries use `PESSIMISTIC_WRITE` on single IDs rather than range scans:
  ```java
  employeeRepository.findByIdWithPessimisticLock(employeeId);
  leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYearWithLock(employeeId, typeId, year);
  ```

---

## 4. Playbook 3: Liquibase Changelog Lock Staleness

### Symptoms
- Application startup hangs indefinitely with:
  ```text
  2026-11-11T08:00:00.000 [main] INFO liquibase.lockservice : Waiting for changelog lock....
  ```
- Container fails healthcheck and restarts in a crash loop.

### Root Causes
- The previous container instance was killed abruptly (via `SIGKILL`, OOM killer, or hardware power loss) while applying Liquibase database migrations, leaving `LOCKED = TRUE` in `DATABASECHANGELOGLOCK`.

### Diagnostic Steps
Query the lock table in PostgreSQL:
```bash
docker exec -it lms-postgres psql -U lms_app_user -d leave_management_db -c "SELECT * FROM DATABASECHANGELOGLOCK;"
```

### Remediation
1. Verify no other instance is currently performing migrations.
2. Force release the lock:
   ```bash
   docker exec -it lms-postgres psql -U lms_app_user -d leave_management_db -c "UPDATE DATABASECHANGELOGLOCK SET LOCKED = FALSE, LOCKGRANTED = NULL, LOCKEDBY = NULL WHERE ID = 1;"
   ```
3. Restart the backend application:
   ```bash
   docker compose restart app
   ```

---

## 5. Playbook 4: Idempotency Key Conflicts & In-Progress Collisions

### Symptoms
- Client receives `409 Conflict`:
  ```json
  {
    "status": 409,
    "error": "Conflict",
    "message": "A request with this idempotency key is currently in progress. Please retry shortly."
  }
  ```
- Or:
  ```json
  {
    "status": 409,
    "error": "Conflict",
    "message": "Idempotency key payload mismatch: Request parameters do not match the original request for this key."
  }
  ```

### Root Causes
1. **Parallel Double-Click**: Client sent rapid concurrent requests with identical `Idempotency-Key` headers before the first request completed.
2. **Orphaned In-Progress Key**: The server crashed while processing an in-flight request, leaving status `IN_PROGRESS` in `idempotency_keys`.
3. **Key Reuse**: Client reused the same key with different parameters.

### Diagnostic Steps
Inspect idempotency health status:
```bash
curl -s -H "Authorization: Bearer <ADMIN_TOKEN>" http://localhost:8080/api/audit/transactions/idempotency | jq .
```

### Remediation
- **Manual Cleanup**: Trigger cleanup of interrupted and expired keys:
  ```bash
  curl -X POST -H "Authorization: Bearer <ADMIN_TOKEN>" http://localhost:8080/api/audit/transactions/idempotency/cleanup
  ```
- **Client Resolution**: If payload mismatch occurred, instruct the client application to generate a fresh UUID v4 for distinct business operations.

---

## 6. Playbook 5: OutOfMemoryError (OOM) & JVM Memory Degraded

### Symptoms
- Container terminates with exit code 137 (`OOMKilled`).
- `/api/audit/performance/system-summary` reports `"status": "DEGRADED"` with `heapUsagePercentage > 90%`.
- High latency on all endpoints due to excessive G1GC pause times.

### Diagnostic Steps
1. Inspect runtime heap memory telemetry:
   ```bash
   curl -s -H "Authorization: Bearer <ADMIN_TOKEN>" http://localhost:8080/api/audit/performance/system-summary | jq .
   ```
2. Trigger automated heap dump analysis:
   Ensure JVM arguments contain:
   `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/dumps/oom.hprof`
3. Check container memory usage in Docker:
   ```bash
   docker stats lms-backend
   ```

### Remediation
1. Adjust container memory reservations and limits in `docker-compose.yml`:
   ```yaml
   deploy:
     resources:
       limits:
         memory: 2048M
       reservations:
         memory: 1024M
   ```
2. Ensure JVM container memory flags are passed:
   ```bash
   -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC
   ```
3. Audit pagination: Ensure all list endpoints enforce max page size (default 20, max 100) to prevent loading thousands of records into heap.

---

## 7. Playbook 6: Authentication & Security Token Anomalies

### Symptoms
- Valid user receives `401 Unauthorized` with `Token expired or invalid`.
- User account gets locked with `Account is locked due to too many failed attempts`.

### Diagnostic Steps
1. Check recent security events and failed login attempts:
   ```bash
   curl -s -H "Authorization: Bearer <ADMIN_TOKEN>" "http://localhost:8080/api/security-events?page=0&size=20" | jq .
   ```
2. Check login history for specific user:
   ```bash
   curl -s -H "Authorization: Bearer <ADMIN_TOKEN>" "http://localhost:8080/api/login-history/user/<USER_ID>" | jq .
   ```

### Remediation
- **Account Unlock**: If locked by brute-force protection (5 failed attempts trigger 15-minute lockout), an administrator can unlock the user:
  ```bash
  curl -X POST -H "Authorization: Bearer <ADMIN_TOKEN>" http://localhost:8080/api/users/<USER_ID>/unlock
  ```
- **Token Clock Skew**: Ensure system clocks on host and client are synchronized via NTP (`chrony` or `systemd-timesyncd`).

---

## 8. Diagnostic Commands Quick-Reference Sheet

```bash
# Health & Actuator Status
curl -i http://localhost:8080/actuator/health

# Connection Pool Telemetry
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/audit/performance/pool-status | jq .

# Database Index & N+1 Verification
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/audit/performance/query-diagnostics | jq .

# System CPU, Heap & SLA Targets
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/audit/performance/system-summary | jq .

# Balance Invariant Integrity Audit
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/audit/transactions/integrity | jq .

# Idempotency Health & Cleanup
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/audit/transactions/idempotency/cleanup | jq .

# Docker Container Resource Usage
docker stats --no-stream

# View Live Application Logs
docker compose logs -f --tail=100 app
```

