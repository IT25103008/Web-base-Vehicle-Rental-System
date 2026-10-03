package com.vehiclerental.exception;

// Thrown when someone tries to move a booking/handover/claim into
// a status it isn't allowed to reach from its current status.
// HTTP 400.
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
