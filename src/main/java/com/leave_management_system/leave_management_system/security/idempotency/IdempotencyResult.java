package com.leave_management_system.leave_management_system.security.idempotency;

import java.util.Collections;
import java.util.Map;

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
    private final String contentType;
    private final Map<String, String> responseHeaders;

    private IdempotencyResult(Type type, Integer responseStatus, String responseBody, String contentType, Map<String, String> responseHeaders) {
        this.type = type;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.contentType = contentType;
        this.responseHeaders = responseHeaders != null ? responseHeaders : Collections.emptyMap();
    }

    public static IdempotencyResult newExecution() {
        return new IdempotencyResult(Type.NEW_EXECUTION, null, null, null, null);
    }

    public static IdempotencyResult replay(int status, String body, String contentType, Map<String, String> responseHeaders) {
        return new IdempotencyResult(Type.REPLAY, status, body, contentType, responseHeaders);
    }

    public static IdempotencyResult replay(int status, String body) {
        return new IdempotencyResult(Type.REPLAY, status, body, "application/json", null);
    }

    public static IdempotencyResult payloadMismatch() {
        return new IdempotencyResult(Type.PAYLOAD_MISMATCH, null, null, null, null);
    }

    public static IdempotencyResult inProgress() {
        return new IdempotencyResult(Type.IN_PROGRESS, null, null, null, null);
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

    public String getContentType() {
        return contentType;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }
}
