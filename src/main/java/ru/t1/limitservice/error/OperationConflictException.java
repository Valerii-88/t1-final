package ru.t1.limitservice.error;

public class OperationConflictException extends RuntimeException {

    public OperationConflictException(String message) {
        super(message);
    }
}
