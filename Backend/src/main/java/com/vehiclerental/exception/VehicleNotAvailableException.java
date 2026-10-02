package com.vehiclerental.exception;

// Thrown when someone tries to book/rent a vehicle that isn't Available.
// Global handler maps this to HTTP 409 (conflict).
public class VehicleNotAvailableException extends RuntimeException {
    public VehicleNotAvailableException(String message) {
        super(message);
    }
}
