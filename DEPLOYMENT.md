# Enterprise Production Deployment Guide
## Leave Management System (LMS)

This document provides complete instructions for provisioning, configuring, deploying, and maintaining the Leave Management System in staging and production enterprise environments.

---

## 1. Architecture & Container Topology

The LMS architecture is container-native, utilizing a hardened multi-stage Docker build, Spring Boot 3 running on Java 21, and PostgreSQL 15 with automated Liquibase migrations:

```
                                  INTERNET / CLIENTS
                                          │
                                          ▼
                               ┌─────────────────────┐
                               │  Reverse Proxy / LB │
                               │   (Nginx / Traefik) │
                               └──────────┬──────────┘
                                          │ TLS (443)
                                          ▼
                         ┌─────────────────────────────────┐
                         │      Docker: lms-public-net     │
                         │                                 │
                         │   ┌─────────────────────────┐   │
                         │   │   Spring Boot Backend   │   │
                         │   │       (lms-backend)     │   │
                         │   │  Port: 8080 (Non-root)  │   │
                         │   └────────────┬────────────┘   │
                         └────────────────┼────────────────┘
                                          │
                         ┌────────────────┼────────────────┐
                         │   ┌────────────▼────────────┐   │
                         │   │      PostgreSQL 15      │   │
                         │   │      (lms-postgres)     │   │
                         │   │   Port: 5432 (Internal) │   │
                         │   └─────────────────────────┘   │
                         │                                 │
                         │     Docker: lms-internal-net    │
                         │     (internal: true, isolated)  │
                         └─────────────────────────────────┘
```

### Network Isolation
- **`lms-public-net`**: Bridge network exposing the backend port (`8080`) to the host or reverse proxy.
- **`lms-internal-net`**: Hardened internal network (`internal: true`) with no outbound internet access. Only `lms-backend` can communicate with `lms-postgres` on port 5432. The database is strictly not bound to any public interface.

---

## 2. Prerequisites & Resource Requirements

| Component | Minimum Specification | Recommended Production |
|-----------|----------------------|------------------------|
| **Operating System** | Linux (Ubuntu 22.04 LTS / RHEL 9) | Linux (Ubuntu 22.04 LTS / RHEL 9) |
| **CPU Architecture** | x86_64 or arm64 (2 Cores) | x86_64 or arm64 (4 to 8 Cores) |
| **RAM** | 2 GB Available | 4 GB to 8 GB Available |
| **Disk Storage** | 20 GB SSD | 50 GB+ High-IOPS NVMe |
| **Docker Engine** | Version 24.0+ | Version 26.0+ |
| **Docker Compose** | Compose v2.20+ | Compose v2.27+ |

---

## 3. Configuration Management & Environment Variables

Create a secure `.env` file in the project root with strictly restricted permissions (`chmod 600 .env`):

```bash
# ==============================================================================
# Leave Management System - Production Environment Variables
# ==============================================================================

# Application Runtime
SPRING_PROFILES_ACTIVE=prod
APP_PORT=8080

# Database Connectivity
DB_URL=jdbc:postgresql://db:5432/leave_management_db
DB_NAME=leave_management_db
DB_USERNAME=lms_app_user
DB_PASSWORD=YourStrongDatabasePasswordHere123!
DB_PORT=5432

# Security & Cryptography
# Generate with: openssl rand -base64 64
JWT_SECRET=c2VjdXJlLXByb2R1Y3Rpb24tand0LXNlY3JldC1rZXktd2l0aC1zdWZmaWNpZW50LWVudHJvcHktMjU2LWJpdHM=
JWT_EXPIRATION=900000              # 15 minutes in milliseconds
JWT_REFRESH_EXPIRATION=604800000    # 7 days in milliseconds

# AES-256 GCM Sensitive Data Encryption Key (Must be exactly 32 bytes Base64 or Hex)
# Generate with: openssl rand -base64 32
ENCRYPTION_KEY=12345678901234567890123456789012

# Network Security
SECURITY_TRUSTED_PROXIES=127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
```

> [!CAUTION]
> Never commit `.env` or plaintext passwords to version control. In Kubernetes or cloud environments (AWS/GCP/Azure), inject these variables via Kubernetes Secrets, AWS Secrets Manager, or HashiCorp Vault.

---

## 4. Multi-Stage Docker Build & Hardening

The application is containerized using `Dockerfile` featuring a two-stage build:

1. **Stage 1 (`builder`)**: Uses `maven:3.9.6-eclipse-temurin-21-jammy` to resolve dependencies and compile the production JAR. Maven layer caching is leveraged (`mvn dependency:go-offline`).
2. **Stage 2 (`runner`)**: Uses `eclipse-temurin:21-jre-jammy` minimal runtime:
   - System non-root user `appuser:appgroup` (UID/GID 10001).
   - JVM container-aware memory allocation:
     `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0`
   - Built-in container healthcheck validating `/actuator/health`.

### Manual Build Command
```bash
docker build -t leave-management-system:1.0.0 -f Dockerfile .
```

---

## 5. Step-by-Step Deployment Instructions

### Option A: Automated Docker Compose Deployment (Recommended for Single-Node / Staging)

1. **Clone the repository and prepare the working directory**:
   ```bash
   git clone <repo-url> /opt/lms
   cd /opt/lms
   ```

2. **Configure environment credentials**:
   ```bash
   cp .env.example .env
   # Edit .env with production passwords and keys
   chmod 600 .env
   ```

3. **Launch all services in detached mode**:
   ```bash
   docker compose up -d --build
   ```

4. **Verify container health and logs**:
   ```bash
   docker compose ps
   docker compose logs -f app
   ```

5. **Verify database migration completion**:
   Ensure logs indicate:
   ```text
   liquibase.lockservice: Successfully released change log lock
   liquibase.command: Update command completed successfully
   ```

6. **Validate the live health endpoint**:
   ```bash
   curl -f http://localhost:8080/actuator/health
   # Expected: {"status":"UP"}
   ```

---

### Option B: Bare-Metal / Virtual Machine Systemd Deployment

1. **Prerequisites**: Install OpenJDK 21 and PostgreSQL 15.
2. **Create Database & User**:
   ```sql
   CREATE DATABASE leave_management_db;
   CREATE USER lms_app_user WITH ENCRYPTED PASSWORD 'StrongPassword123!';
   GRANT ALL PRIVILEGES ON DATABASE leave_management_db TO lms_app_user;
   ```

3. **Build the Application**:
   ```bash
   ./mvnw clean package -DskipTests
   sudo mkdir -p /opt/lms
   sudo cp target/leave-management-system.jar /opt/lms/app.jar
   sudo useradd -r -s /bin/false lms
   sudo chown -R lms:lms /opt/lms
   ```

4. **Install Systemd Service Unit (`/etc/systemd/system/lms.service`)**:
   ```ini
   [Unit]
   Description=Leave Management System Spring Boot Service
   After=network.target postgresql.service

   [Service]
   Type=simple
   User=lms
   Group=lms
   WorkingDirectory=/opt/lms
   EnvironmentFile=/opt/lms/.env
   ExecStart=/usr/bin/java -XX:+UseG1GC -Xms1024m -Xmx2048m -jar /opt/lms/app.jar
   Restart=always
   RestartSec=10
   StandardOutput=journal
   StandardError=journal
   SuccessExitStatus=143

   [Install]
   WantedBy=multi-user.target
   ```

5. **Enable and Start Service**:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable lms
   sudo systemctl start lms
   sudo systemctl status lms
   ```

---

## 6. Deployment Strategies & Zero-Downtime Analysis

### 6.1 Architectural Analysis: Does the Current Docker Compose Support Zero-Downtime?

**Definitive Architectural Finding**: **No. Zero-downtime deployment is NOT genuinely supported by the current single-container Docker Compose architecture.**

While the application code supports graceful shutdown, deploying via single-node Docker Compose incurs an unavoidable **10 to 25 second maintenance window (service downtime)** during container recreation.

#### Why Zero-Downtime is NOT Supported in the Current Compose Topology:
1. **Direct Host Port Collision (`ports: "8080:8080"`)**:
   Host port 8080 is bound directly to `lms-backend`. The operating system's TCP socket stack allows only one process to listen on port 8080 at a time. A new container cannot start on port 8080 until the old container has stopped and released the socket.
2. **Fixed Container Name (`container_name: lms-backend`)**:
   Docker enforces unique container names. A new version cannot be created alongside the active container with the same name.
3. **Absence of a Dynamic Reverse Proxy / Ingress**:
   Without an edge router (e.g., Nginx, Traefik, Envoy, or HAProxy) in front of the application, there is no mechanism to buffer incoming connections or route traffic between blue/green upstream targets based on health probes.
4. **JVM Warm-up & Context Initialization Window**:
   When `docker compose up -d --no-deps app` runs:
   - The old container stops (in-flight requests complete within 30s via graceful shutdown).
   - Port 8080 closes.
   - The new container starts the OpenJDK 21/25 JVM.
   - Spring Boot initializes classpath components (3–5s).
   - Liquibase acquires lock and validates 26 changesets against PostgreSQL (2–4s).
   - HikariCP pool allocates database connections (1–2s).
   - Tomcat binds to port 8080 and Actuator reports `UP`.
   - **Total gap: 10 to 25 seconds where all incoming requests receive `ECONNREFUSED` (Connection Refused)**.

---

### 6.2 Validated Production Deployment Procedure

For environments where a 15-second scheduled maintenance window is acceptable:

1. **Pre-Deployment Pre-Flight**:
   ```bash
   git pull origin main
   chmod 600 .env.prod
   # Verify mandatory production secrets
   grep -q "REPLACE_WITH" .env.prod && { echo "ERROR: Placeholder secrets found"; exit 1; }
   ```

2. **Pre-Deployment Database Snapshot**:
   ```bash
   docker exec -t lms-postgres pg_dump -U ${DB_USERNAME:-postgres} -d ${DB_NAME:-leave_management_db} \
     --clean --if-exists --format=plain | gzip > "/var/backups/lms/pre_deploy_$(date +%Y%m%d_%H%M%S).sql.gz"
   ```

3. **Build the New Container Image**:
   ```bash
   # Building prior to stopping avoids downtime during the image compilation phase
   docker compose build app
   ```

4. **Execute Controlled Container Recreation**:
   ```bash
   docker compose up -d --no-deps app
   ```

5. **Post-Deployment Validation & Health Check**:
   ```bash
   docker compose ps
   docker compose logs -f --tail=30 app
   curl -f http://localhost:8080/actuator/health
   # Expected: {"status":"UP"}
   ```

---

### 6.3 Validated Rollback Procedures

#### Procedure A: Application Code Rollback (No Schema Incompatibility)
When rolling back application logic or fixing a runtime regression where database changes are backward-compatible:

```bash
# 1. Identify previous verified release tag or commit SHA
git log -2 --oneline

# 2. Checkout the previous known-good release tag
git checkout <previous-known-good-tag>

# 3. Rebuild and restart the application container
docker compose build app
docker compose up -d --no-deps app

# 4. Verify restored health
curl -f http://localhost:8080/actuator/health
```

#### Procedure B: Emergency Full Disaster Rollback (Database & Application Reversal)
When a deployment fails due to schema divergence, corrupted data, or unrecoverable exceptions:

```bash
# 1. Immediately stop the application to isolate incoming traffic
docker compose stop app

# 2. Restore the pre-deployment database snapshot
LATEST_BACKUP=$(ls -t /var/backups/lms/pre_deploy_*.sql.gz | head -1)
echo "Restoring from snapshot: $LATEST_BACKUP"
gunzip -c "$LATEST_BACKUP" | docker exec -i lms-postgres psql -U ${DB_USERNAME:-postgres} -d ${DB_NAME:-leave_management_db}

# 3. Checkout and deploy the previous known-good application release
git checkout <previous-known-good-tag>
docker compose up -d --no-deps --build app

# 4. Validate post-rollback operational state
curl -f http://localhost:8080/actuator/health
docker exec -t lms-postgres psql -U postgres -d leave_management_db -c "SELECT COUNT(*) FROM databasechangelog;"
```

---

### 6.4 How to Achieve Genuine Zero-Downtime (Target Architectures)

To eliminate the 15-second downtime window and achieve true zero-downtime:

#### Option 1: Blue/Green Docker Compose with Nginx Reverse Proxy
```
           ┌─────────────────────────────┐
           │     Nginx Ingress Proxy     │
           │  (Host Port 8080 / 443)     │
           └──────────────┬──────────────┘
                          │ (Zero-Downtime Reload)
                ┌─────────┴─────────┐
                ▼                   ▼
      ┌──────────────────┐ ┌──────────────────┐
      │  app-blue:8080   │ │  app-green:8080  │
      │  (Active Prod)   │ │  (Staging/New)   │
      └─────────┬────────┘ └─────────┬────────┘
                │                    │
                └─────────┬──────────┘
                          ▼
              ┌───────────────────────┐
              │  PostgreSQL (Port 5432)│
              └───────────────────────┘
```
1. Add an Nginx service bound to host port 8080.
2. Define two internal app services: `app-blue` and `app-green` (no host port binding).
3. Deploy new code to the inactive color (`green`).
4. Wait for `green` healthcheck (`/actuator/health` -> `200 OK`).
5. Reload Nginx configuration (`docker exec nginx nginx -s reload`) to point upstream to `green` (0 ms cutover).
6. Terminate `blue`.

#### Option 2: Kubernetes Rolling Update (`maxSurge: 1, maxUnavailable: 0`)
Deploy onto Kubernetes where native rolling updates spin up the new pod, wait for the readiness probe (`readinessProbe: httpGet: path: /actuator/health, port: 8080`) to pass, register the new pod with the Service endpoints, and only then terminate the old pod. Zero dropped connections.

---

## 7. Database Backup & Disaster Recovery Automation

### Automated Backup Script (`/opt/lms/backup.sh`)
```bash
#!/bin/bash
set -euo pipefail

BACKUP_DIR="/var/backups/lms"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/lms_db_${TIMESTAMP}.sql.gz"
RETENTION_DAYS=14

mkdir -p "${BACKUP_DIR}"

echo "[INFO] Starting database backup: ${BACKUP_FILE}"
docker exec lms-postgres pg_dump -U lms_app_user -d leave_management_db | gzip > "${BACKUP_FILE}"
chmod 600 "${BACKUP_FILE}"

echo "[INFO] Pruning backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DIR}" -type f -name "lms_db_*.sql.gz" -mtime +${RETENTION_DAYS} -delete

echo "[INFO] Backup completed successfully."
```

### Scheduled Cron Job (`crontab -e`)
```cron
# Run daily database backup at 02:00 UTC
0 2 * * * /opt/lms/backup.sh >> /var/log/lms_backup.log 2>&1
```

### Restoration Runbook
```bash
# 1. Stop backend application to terminate active connection leases
docker compose stop app

# 2. Decompress and restore SQL dump
gunzip -c /var/backups/lms/lms_db_20261111_020000.sql.gz | docker exec -i lms-postgres psql -U lms_app_user -d leave_management_db

# 3. Restart application and verify health
docker compose start app
curl -f http://localhost:8080/actuator/health
```

---

## 8. Runtime Telemetry & Performance Monitoring

Administrators can monitor the live production system using dedicated audit REST endpoints:

| Endpoint | Method | Required Authority | Description |
|----------|--------|---------------------|-------------|
| `/actuator/health` | `GET` | Public (or Authorized) | Core container and database ping |
| `/api/audit/performance/pool-status` | `GET` | `ADMIN_VIEW` / `SYSTEM_AUDIT` | HikariCP active/idle leases, awaiting threads |
| `/api/audit/performance/query-diagnostics` | `GET` | `ADMIN_VIEW` / `SYSTEM_AUDIT` | Monitored table indexes & N+1 protections |
| `/api/audit/performance/system-summary` | `GET` | `ADMIN_VIEW` / `SYSTEM_AUDIT` | JVM heap MB, CPU load average, SLA targets |
| `/api/audit/transactions/integrity` | `GET` | `ADMIN_VIEW` / `SYSTEM_AUDIT` | Balance equation invariants ($total = used + available$) |

