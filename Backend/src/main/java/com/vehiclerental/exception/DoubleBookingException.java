package com.vehiclerental.exception;

// Thrown when a booking overlaps an existing one for the same vehicle.
// HTTP 409.
public class    DoubleBookingException extends RuntimeException {
    public DoubleBookingException(String message) {
        super(message);
    }
}
