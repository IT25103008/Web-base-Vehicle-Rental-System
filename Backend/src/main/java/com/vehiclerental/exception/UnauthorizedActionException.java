package com.vehiclerental.exception;

// Thrown when a role-based rule is broken (e.g. a customer tries to
// view someone else's booking). HTTP 403.
public class UnauthorizedActionException extends RuntimeException {
    public UnauthorizedActionException(String message) {
        super(message);
    }
}
