package ru.t1.limitservice.error;

public class InsufficientLimitException extends RuntimeException {

    public InsufficientLimitException(String message) {
        super(message);
    }
}
