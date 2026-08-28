package com.leave_management_system.leave_management_system.exception;

public class InsufficientLeaveException extends RuntimeException {
    public InsufficientLeaveException(String message) {
        super(message);
    }
}
