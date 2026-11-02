# ==============================================================================
# Stage 1: Build & Package Application
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-21-jammy AS builder

WORKDIR /build

# 1. Cache Maven dependencies separately to optimize Docker layer caching.
# Downloading dependencies only invalidates when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Copy source code and build production executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Hardened Production Runtime Image
# ==============================================================================
FROM eclipse-temurin:21-jre-jammy AS runner

# Metadata labels
LABEL maintainer="Leave Management System Team" \
      version="1.0.0" \
      description="Production container for Leave Management System"

# 1. Install curl for healthcheck validation and clean apt cache
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 2. Create non-privileged system user and group (Principle of Least Privilege)
RUN groupadd -r -g 10001 appgroup \
    && useradd -r -u 10001 -g appgroup -m -d /app -s /sbin/nologin appuser

WORKDIR /app

# 3. Copy the packaged JAR from builder stage with strict ownership
COPY --from=builder --chown=appuser:appgroup /build/target/leave-management-system.jar app.jar

# 4. Switch to non-root user
USER appuser:appgroup

# 5. Expose HTTP port
EXPOSE 8080

# 6. Container-optimized JVM flags and defaults
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE="prod" \
    SERVER_PORT=8080

# 7. Native Container Healthcheck via Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -f "http://localhost:${SERVER_PORT}/actuator/health" || exit 1

# 8. Start application with exec form to properly handle OS signals (SIGTERM/SIGINT)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
