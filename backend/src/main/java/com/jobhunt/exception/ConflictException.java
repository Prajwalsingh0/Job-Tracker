package com.jobhunt.exception;

/** Thrown when a request conflicts with existing state, e.g. a duplicate email. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
