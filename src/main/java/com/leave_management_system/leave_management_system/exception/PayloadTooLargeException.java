package com.leave_management_system.leave_management_system.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}

