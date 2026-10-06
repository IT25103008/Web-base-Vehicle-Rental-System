package com.vehiclerental.exception;

// Thrown when a findById(...) returns nothing.
// Global handler maps this to HTTP 404.
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
