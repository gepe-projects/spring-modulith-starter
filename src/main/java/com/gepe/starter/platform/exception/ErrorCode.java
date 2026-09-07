package com.gepe.starter.platform.exception;

import org.springframework.http.HttpStatus;

/**
 * Contract for every application error code (see §6 of {@code agents.md}).
 *
 * <p>Each module owns an enum implementing this interface — e.g.
 * {@code UserError} inside module {@code user} — where one constant bundles the
 * HTTP status and the message key of one error. The {@link GlobalExceptionHandler}
 * only sees this interface: it maps the status from the code and resolves the
 * key through the aggregated MessageSource, so platform code never depends on a
 * module bundle and modules never produce hard-coded text.
 *
 * <p>Shared, cross-cutting codes (rate limit, duplicate entry, …) live in
 * {@link GlobalError}; anything domain-specific must NOT be added there but in
 * the owning module's own enum.
 */
public interface ErrorCode {

    /** HTTP status that should be returned when this error is thrown. */
    HttpStatus getHttpStatus();

    /** Message key resolved through the aggregated MessageSource. */
    String getMessageKey();
}
