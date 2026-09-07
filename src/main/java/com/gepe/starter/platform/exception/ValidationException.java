package com.gepe.starter.platform.exception;

import java.util.List;

import com.gepe.starter.platform.web.response.ValidationError;
import lombok.Getter;

/**
 * Field-level validation failure detected in the service layer (see §6 of
 * {@code agents.md}) — used when business rules reject individual fields in a
 * way bean validation on the request DTO cannot express.
 *
 * <p>Errors are already localized when thrown (resolved through the aggregated
 * MessageSource), exactly like the per-field errors produced by bean
 * validation; the {@link GlobalExceptionHandler} maps this to {@code 400} with
 * headline {@code validation.failed}.
 */
@Getter
public class ValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public ValidationException(List<ValidationError> errors) {
        super("Validation failed");
        this.errors = List.copyOf(errors);
    }

}
