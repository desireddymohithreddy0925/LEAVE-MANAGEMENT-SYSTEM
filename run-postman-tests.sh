#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PORT="${PORT:-8089}"
BASE_URL="http://localhost:${PORT}"
COLLECTION_FILE="postman/Leave_Management_System.postman_collection.json"
ENV_FILE="postman/Leave_Management_System.postman_environment.json"
REPORT_DIR="target/newman-reports"

export JWT_SECRET="${JWT_SECRET:-testOnlySecretKeyForUnitAndIntegrationTestsThatIsAtLeast32BytesLong!}"
export ENCRYPTION_KEY="${ENCRYPTION_KEY:-testOnlyMasterEncryptionKeyForUnitAndIntegrationTests256Bit!}"

mkdir -p "$REPORT_DIR"

echo "=========================================================="
echo " Starting Postman Validation Test Suite"
echo " Target URL: $BASE_URL"
echo " Collection: $COLLECTION_FILE"
echo " Environment: $ENV_FILE"
echo "=========================================================="

SERVER_PID=""

# Check if an instance is already listening on PORT
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null ; then
    echo "Existing service detected on port $PORT. Using existing instance."
else
    echo "Starting Spring Boot application on port $PORT (test profile)..."
    ./mvnw spring-boot:run \
        -Dspring-boot.run.arguments="--spring.profiles.active=test --server.port=$PORT --app.jwt.secret=$JWT_SECRET --jwt.secret=$JWT_SECRET --app.security.encryption.key=$ENCRYPTION_KEY" \
        > "$REPORT_DIR/spring-boot.log" 2>&1 &
    SERVER_PID=$!
    echo "Application started with PID: $SERVER_PID"

    cleanup() {
        if [ -n "$SERVER_PID" ]; then
            echo "Shutting down application (PID: $SERVER_PID)..."
            kill -9 "$SERVER_PID" 2>/dev/null || true
        fi
    }
    trap cleanup EXIT INT TERM

    echo "Waiting for application to become healthy at $BASE_URL/actuator/health..."
    MAX_ATTEMPTS=45
    COUNT=0
    until curl -s -f "$BASE_URL/actuator/health" > /dev/null 2>&1 || [ $COUNT -ge $MAX_ATTEMPTS ]; do
        sleep 2
        COUNT=$((COUNT + 1))
        echo -n "."
    done
    echo ""

    if [ $COUNT -ge $MAX_ATTEMPTS ]; then
        echo "ERROR: Server failed to start within $((MAX_ATTEMPTS * 2)) seconds."
        echo "Recent logs:"
        tail -n 40 "$REPORT_DIR/spring-boot.log"
        exit 1
    fi
    echo "Server is healthy and ready!"
fi

echo "=========================================================="
echo " Executing Newman Postman Collection Runner..."
echo "=========================================================="

npx --yes newman run "$COLLECTION_FILE" \
    --environment "$ENV_FILE" \
    --env-var "baseUrl=$BASE_URL" \
    --reporters cli,json \
    --reporter-json-export "$REPORT_DIR/newman-results.json" \
    --color on

echo "=========================================================="
echo " Postman validation suite completed successfully!"
echo " Results exported to $REPORT_DIR/newman-results.json"
echo "=========================================================="

