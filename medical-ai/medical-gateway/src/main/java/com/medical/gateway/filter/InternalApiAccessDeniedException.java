package com.medical.gateway.filter;

public class InternalApiAccessDeniedException extends RuntimeException {

    public InternalApiAccessDeniedException(String message) {
        super(message);
    }
}
