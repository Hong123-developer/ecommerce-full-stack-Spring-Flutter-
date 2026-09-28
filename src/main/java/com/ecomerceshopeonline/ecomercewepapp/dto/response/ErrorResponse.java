package com.ecomerceshopeonline.ecomercewepapp.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        /** Present only for validation failures: field name -> first violation message. */
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(false, status, error, message, path, LocalDateTime.now(), null);
    }

    public static ErrorResponse validation(int status, String message, String path,
                                           Map<String, String> fieldErrors) {
        return new ErrorResponse(false, status, "Validation Failed", message, path,
                LocalDateTime.now(), fieldErrors);
    }
}
