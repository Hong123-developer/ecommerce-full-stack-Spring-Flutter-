package com.ecomerceshopeonline.ecomercewepapp.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for every exception the API deliberately turns into a client-facing response.
 * Anything that is not an {@code ApiException} is treated as a bug and reported as a 500.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
