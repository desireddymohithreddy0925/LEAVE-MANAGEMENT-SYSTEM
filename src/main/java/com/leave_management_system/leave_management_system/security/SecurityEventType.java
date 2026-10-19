package com.leave_management_system.leave_management_system.security;

public final class SecurityEventType {
    public static final String SUCCESSFUL_LOGIN = "SUCCESSFUL_LOGIN";
    public static final String FAILED_LOGIN = "FAILED_LOGIN";
    public static final String USER_LOGOUT = "USER_LOGOUT";
    public static final String EXPIRED_TOKEN = "EXPIRED_TOKEN";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String UNAUTHORIZED_ACCESS = "UNAUTHORIZED_ACCESS";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String INACTIVE_USER_TOKEN = "INACTIVE_USER_TOKEN";
    public static final String TOKEN_REFRESHED = "TOKEN_REFRESHED";
    public static final String UNKNOWN_USER_TOKEN = "UNKNOWN_USER_TOKEN";

    public static final String ENTITY_TYPE_SECURITY_EVENT = "SECURITY_EVENT";

    private SecurityEventType() {
    }
}
