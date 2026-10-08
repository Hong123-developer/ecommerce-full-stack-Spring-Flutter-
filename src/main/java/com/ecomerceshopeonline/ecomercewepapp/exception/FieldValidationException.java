package com.ecomerceshopeonline.ecomercewepapp.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A 400 for a rule bean validation cannot express (it needs the database or several fields
 * at once). Rendered like a bean-validation failure, with {@code fieldErrors}, so forms can
 * highlight the field.
 */
@Getter
public class FieldValidationException extends BadRequestException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(String field, String message) {
        super(message);
        this.fieldErrors = Map.of(field, message);
    }

    /**
     * Several fields at once (field name -> message), so a form can mark all of them in one
     * round trip. The top-level message is the only error's, or a summary when there are more.
     */
    public FieldValidationException(Map<String, String> fieldErrors) {
        super(fieldErrors.size() == 1
                ? fieldErrors.values().iterator().next()
                : "One or more fields are invalid");
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }
}
