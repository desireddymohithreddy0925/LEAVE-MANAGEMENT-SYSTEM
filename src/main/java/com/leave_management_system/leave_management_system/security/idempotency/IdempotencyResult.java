package com.leave_management_system.leave_management_system.security.idempotency;

public class IdempotencyResult {

    public enum Type {
        NEW_EXECUTION,
        REPLAY,
        PAYLOAD_MISMATCH,
        IN_PROGRESS
    }

    private final Type type;
    private final Integer responseStatus;
    private final String responseBody;

    private IdempotencyResult(Type type, Integer responseStatus, String responseBody) {
        this.type = type;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    public static IdempotencyResult newExecution() {
        return new IdempotencyResult(Type.NEW_EXECUTION, null, null);
    }

    public static IdempotencyResult replay(int status, String body) {
        return new IdempotencyResult(Type.REPLAY, status, body);
    }

    public static IdempotencyResult payloadMismatch() {
        return new IdempotencyResult(Type.PAYLOAD_MISMATCH, null, null);
    }

    public static IdempotencyResult inProgress() {
        return new IdempotencyResult(Type.IN_PROGRESS, null, null);
    }

    public boolean isNewExecution() {
        return type == Type.NEW_EXECUTION;
    }

    public boolean isReplay() {
        return type == Type.REPLAY;
    }

    public boolean isPayloadMismatch() {
        return type == Type.PAYLOAD_MISMATCH;
    }

    public boolean isInProgress() {
        return type == Type.IN_PROGRESS;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
