package ru.t1.limitservice.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.t1.limitservice.api.ErrorResponse;

import java.time.OffsetDateTime;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientLimitException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientLimit(
            InsufficientLimitException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "INSUFFICIENT_LIMIT", exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(OperationConflictException.class)
    public ResponseEntity<ErrorResponse> handleOperationConflict(
            OperationConflictException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "OPERATION_CONFLICT", exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(OperationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOperationNotFound(
            OperationNotFoundException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "OPERATION_NOT_FOUND", exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "Validation failed" : Objects.requireNonNullElse(fieldError.getDefaultMessage(), "Validation failed");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage(), request.getRequestURI());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, String path) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, OffsetDateTime.now(), path));
    }
}
