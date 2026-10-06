package com.vehiclerental.exception;

// Our own runtime exception. Thrown by AbstractJdbcDao whenever
// a SQLException happens. This way, the Service layer never sees
// a raw SQLException — it only sees our unchecked wrapper.
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
