package com.vehiclerental.exception;

import com.vehiclerental.dto.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== Custom exceptions ====================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex,
                                                   HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler({VehicleNotAvailableException.class, DoubleBookingException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }

    // A POST to an upload endpoint that is not multipart at all.
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleNotMultipart(MultipartException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request",
                     "This endpoint expects a file upload", req);
    }

    // Without this the container's own error surfaces as a 500.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                         HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request",
                     "The photo must be 4 MB or smaller", req);
    }

    @ExceptionHandler({InvalidStatusTransitionException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex,
                                                     HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req);
    }

    @ExceptionHandler(UnauthorizedActionException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedActionException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), req);
    }

    // ==================== Security ====================

    // The user lookup itself failed (e.g. database down) — not the caller's fault.
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ApiError> handleAuthServiceError(InternalAuthenticationServiceException ex,
                                                           HttpServletRequest req) {
        log.error("Login failed because the user lookup failed", ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "Login is temporarily unavailable. Please try again.", req);
    }

    // Account disabled by an administrator, or temporarily locked after too
    // many failed attempts. These need their own message: "invalid email or
    // password" would send the person round in circles.
    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<ApiError> handleBlockedAccount(AuthenticationException ex,
                                                         HttpServletRequest req) {
        String message = ex instanceof LockedException
            ? ex.getMessage()
            : "This account has been disabled. Please contact the branch.";
        return build(HttpStatus.FORBIDDEN, "Forbidden", message, req);
    }

    // Wrong email/password on POST /api/auth/login
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password", req);
    }

    // Spring Security's version of "you don't have permission" — from @PreAuthorize.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleSpringAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Please log in first", req);
        }
        return build(HttpStatus.FORBIDDEN, "Forbidden",
                "You don't have permission for this action", req);
    }

    // ==================== Database ====================

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(DataAccessException ex,
                                                     HttpServletRequest req) {
        // Unique key / foreign key problems are the caller's fault -> 409.
        if (ex.getCause() instanceof SQLIntegrityConstraintViolationException sqlEx) {
            String msg = sqlEx.getMessage() == null ? "" : sqlEx.getMessage().toLowerCase();
            // MySQL error 1062 = duplicate key; "unique" covers other databases.
            if (sqlEx.getErrorCode() == 1062 || msg.contains("duplicate") || msg.contains("unique")) {
                return build(HttpStatus.CONFLICT, "Conflict",
                        "A record with the same unique value already exists", req);
            }
            return build(HttpStatus.CONFLICT, "Conflict",
                    "This action conflicts with related records (check referenced IDs, "
                    + "or remove dependent records first)", req);
        }
        log.error("Database error", ex);
        // Don't leak SQL details to the API caller.
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "A database error occurred. Please try again.", req);
    }

    // Spring's own JDBC exceptions — mainly "cannot get a connection"
    // (MySQL not running, wrong password in application.properties).
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiError> handleSpringDataAccess(org.springframework.dao.DataAccessException ex,
                                                           HttpServletRequest req) {
        log.error("Cannot reach the database", ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "The database is unavailable. Please try again later.", req);
    }

    // ==================== Bad requests ====================
    // @Valid failures on request bodies land here.

    // The response carries both a readable message and fieldErrors
    // ({"phoneNumber": "Enter a Sri Lankan phone number..."}), so a form can
    // put each message next to its own box.

    /**
     * Cross-field checks are @AssertTrue methods (isHoursValid, ...). Their
     * errors are filed under the box the user needs to change.
     */
    private static final Map<String, String> FIELD_ALIASES = Map.of(
        "districtValid", "district",
        "hoursValid", "closeTime",
        "expiryAfterStart", "expiryDate");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            String field = FIELD_ALIASES.getOrDefault(err.getField(), err.getField());
            fields.putIfAbsent(field, err.getDefaultMessage());
        }
        List<String> messages = new ArrayList<>(fields.values());
        ex.getBindingResult().getGlobalErrors().forEach(e -> messages.add(e.getDefaultMessage()));
        return invalid(messages, fields, req);
    }

    // Constraints on @RequestParam values, and on a @RequestBody list itself
    // (its size, or each element), are checked by Spring's method validation.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex,
                                                           HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<String> messages = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String name = parameterName(result.getMethodParameter());
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String msg = error.getDefaultMessage();
                if (msg == null) continue;
                messages.add(msg);
                if (name != null) fields.putIfAbsent(name, msg);
            }
        }
        return invalid(messages, fields, req);
    }

    // The same checks raised outside a controller (a @Validated service).
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest req) {
        List<String> messages = ex.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage).toList();
        return invalid(messages, Map.of(), req);
    }

    /** The name the client used: a @RequestParam's own name, else the Java one. */
    private static String parameterName(MethodParameter p) {
        RequestParam rp = p.getParameterAnnotation(RequestParam.class);
        if (rp != null && !rp.name().isEmpty()) return rp.name();
        if (rp != null && !rp.value().isEmpty()) return rp.value();
        return p.getParameterName();
    }

    private ResponseEntity<ApiError> invalid(List<String> messages, Map<String, String> fields,
                                             HttpServletRequest req) {
        String msg = messages.stream().filter(m -> m != null && !m.isBlank()).distinct()
            .collect(Collectors.joining("; "));
        ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "Validation Failed",
            msg.isEmpty() ? "Some details are not valid" : msg, req.getRequestURI());
        if (!fields.isEmpty()) body.setFieldErrors(fields);
        return ResponseEntity.badRequest().body(body);
    }

    // Malformed JSON, wrong date format, unknown enum value in the body...
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request",
                "Request body is missing or malformed (check JSON syntax, dates as yyyy-MM-dd, enum values)", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request",
                "Missing required parameter: " + ex.getParameterName(), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request",
                "Invalid value '" + ex.getValue() + "' for parameter: " + ex.getName(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                           HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", ex.getMessage(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoEndpoint(NoResourceFoundException ex,
                                                     HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Not Found", "No endpoint at this path", req);
    }

    // ==================== Catch-all ====================
    // Anything we didn't anticipate — log it and return a generic 500.
    // More specific handlers above always win over this one.

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAnythingElse(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on " + req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Something went wrong on our side.", req);
    }

    // ---------- Helper ----------

    private ResponseEntity<ApiError> build(HttpStatus status, String errorName,
                                           String message, HttpServletRequest req) {
        ApiError body = new ApiError(status.value(), errorName, message, req.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
