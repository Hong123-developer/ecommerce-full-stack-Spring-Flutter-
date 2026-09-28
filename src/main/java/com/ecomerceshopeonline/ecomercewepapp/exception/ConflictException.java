package com.ecomerceshopeonline.ecomercewepapp.exception;

import org.springframework.http.HttpStatus;

/** Duplicate email, duplicate SKU, and similar uniqueness violations. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
