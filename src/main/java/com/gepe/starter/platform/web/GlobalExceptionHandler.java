package com.gepe.starter.platform.web;

import java.sql.SQLException;
import java.util.List;

import com.gepe.starter.platform.exception.ServiceException;
import com.gepe.starter.platform.exception.ValidationException;
import com.gepe.starter.platform.i18n.MessageHelper;
import com.gepe.starter.platform.web.response.ErrorResponse;
import com.gepe.starter.platform.web.response.ValidationError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Single place that turns every exception surfacing from a controller (any
 * module) into the {@link ErrorResponse} envelope with localized text — see §6
 * of {@code agents.md}.
 *
 * <p>Every failure body carries {@code code} (stable, machine-readable — it
 * always equals the resolved message key) and {@code message} (localized,
 * human-readable — what frontends render); {@code errors} is present only for
 * per-field validation failures.
 *
 * <p>Framework/Spring MVC exceptions are mapped to a stable code from the
 * app-wide bundle ({@code http.*}, {@code validation.failed}, {@code db.*},
 * {@code file.*}); domain errors are thrown by modules as
 * {@link ServiceException} with a module-owned
 * {@link com.gepe.starter.platform.exception.ErrorCode} — the HTTP status and
 * the message key (+ arguments) come from that code. Messages are always
 * resolved through the aggregated {@code MessageSource} for the current request
 * locale — never rendered from exception text.
 *
 * <p>Logging: expected business outcomes and validation failures are logged at
 * {@code debug}; contract violations at {@code warn} without stack trace;
 * unexpected failures at {@code error} with the full stack trace. The MDC
 * {@code requestId} (see {@code CorrelationIdFilter}) is present on every line.
 *
 * <p>One advice for the whole application — modules must not define their own.
 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String VALIDATION_FAILED = "validation.failed";
    private static final String HTTP_BAD_REQUEST = "http.bad_request";

    private final MessageHelper messageHelper;

    // ------------------------------------------------------------------
    // Validation (400)
    // ------------------------------------------------------------------

    /**
     * Bean validation of a {@code @Valid @RequestBody} /
     * {@code ModelAttribute} ({@link org.springframework.web.bind.MethodArgumentNotValidException}
     * extends {@link BindException}). Per-field messages were already
     * interpolated by the validator against the aggregated MessageSource with
     * the request locale, so
     * {@link org.springframework.validation.FieldError#getDefaultMessage()} is
     * the final localized text.
     */
    @ExceptionHandler(BindException.class)
    ResponseEntity<ErrorResponse> handleBindException(BindException ex) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
                .toList();
        log.debug("Validation failed on {} field(s) for target {}", errors.size(), ex.getObjectName());
        return badRequestValidation(errors);
    }

    /**
     * Method validation of controller parameters ({@code @Validated} +
     * constraints on {@code @RequestParam}/{@code @PathVariable}/…). Each
     * {@code ParameterValidationResult} carries its resolvable errors; they are
     * resolved through the aggregated MessageSource with the request locale
     * (via {@link MessageHelper#get(org.springframework.context.MessageSourceResolvable)}),
     * so the response keeps the same per-field {@code errors} contract as
     * body validation instead of an empty list.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<ValidationError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(resolvable -> new ValidationError(
                                String.valueOf(result.getMethodParameter().getParameterName()),
                                messageHelper.get(resolvable))))
                .toList();
        log.debug("Handler method validation failed on {} parameter(s)", ex.getParameterValidationResults().size());
        return badRequestValidation(errors);
    }

    /** Constraint violations raised by {@code @Validated} on services/beans. */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ValidationError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ValidationError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        log.debug("Constraint violation on {} value(s)", errors.size());
        return badRequestValidation(errors);
    }

    // ------------------------------------------------------------------
    // Request parsing / binding (400)
    // ------------------------------------------------------------------

    /**
     * Malformed or unreadable request body. When the JSON parser reports an
     * invalid value for a field (wrong enum constant, wrong type), the response
     * carries the field name and a precise hint as a validation problem;
     * otherwise a generic {@code http.bad_request}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        InvalidFormatException invalid = findInvalidFormat(ex);
        if (invalid != null) {
            String fieldName = invalid.getPath().isEmpty()
                    ? "unknown"
                    : invalid.getPath().get(0).getPropertyName();

            String detail;
            if (invalid.getTargetType() != null && invalid.getTargetType().isEnum()) {
                String accepted = java.util.Arrays.stream(invalid.getTargetType().getEnumConstants())
                        .map(Object::toString)
                        .collect(java.util.stream.Collectors.joining(", "));
                detail = messageHelper.get("http.invalid_enum_field", accepted);
            } else {
                detail = messageHelper.get("http.invalid_field_value", fieldName, invalid.getValue());
            }

            log.debug("Invalid value for field '{}' in request body", fieldName);
            return badRequestValidation(List.of(new ValidationError(fieldName, detail)));
        }
        log.debug("Malformed request body: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, HTTP_BAD_REQUEST);
    }

    /** Invalid {@code @RequestParam} type (e.g. {@code ?page=abc} for an int). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "http.invalid_param_value", ex.getName());
    }

    /** Missing required {@code @RequestParam}. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "http.parameter_required", ex.getParameterName());
    }

    // ------------------------------------------------------------------
    // Routing / HTTP contract (404, 405, 406, 413, 415)
    // ------------------------------------------------------------------

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
        return clientError(HttpStatus.NOT_FOUND, "http.not_found", ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return clientError(HttpStatus.METHOD_NOT_ALLOWED, "http.method_not_allowed", ex);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return clientError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "http.unsupported_media_type", ex);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ErrorResponse> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return clientError(HttpStatus.NOT_ACCEPTABLE, "http.not_acceptable", ex);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return clientError(HttpStatus.CONTENT_TOO_LARGE, "file.too_large", ex);
    }

    // ------------------------------------------------------------------
    // Persistence (409 / 400)
    // ------------------------------------------------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        if (isDuplicateKey(ex)) {
            log.debug("Data integrity violation is a duplicate key");
            return error(HttpStatus.CONFLICT, "db.duplicate_entry");
        }
        log.debug("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.BAD_REQUEST, "db.data_integrity");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleOptimisticLocking(OptimisticLockingFailureException ex) {
        log.debug("Optimistic lock conflict: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, "exception.optimistic_lock");
    }

    // ------------------------------------------------------------------
    // Application exceptions (status + message key from the error code)
    // ------------------------------------------------------------------

    /**
     * Business error thrown by a module with one of its own
     * {@link com.gepe.starter.platform.exception.ErrorCode}s: the HTTP status
     * comes from the code, the body message from its message key (+ arguments)
     * localized for the current request. The key is never rendered to clients
     * unresolved — a missing key degrades to the key itself plus a warn log in
     * {@link MessageHelper}.
     */
    @ExceptionHandler(ServiceException.class)
    ResponseEntity<ErrorResponse> handleServiceException(ServiceException ex) {
        log.debug("ServiceException {} -> {} ({})", ex.getClass().getSimpleName(),
                ex.getErrorCode().getHttpStatus(), ex.getErrorCode().getMessageKey());
        String message = messageHelper.get(ex.getErrorCode().getMessageKey(), ex.getArgs());
        return simple(ex.getErrorCode().getHttpStatus(), ex.getErrorCode().getMessageKey(), message);
    }

    /** Field-level validation failure raised from the service layer (400). */
    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ErrorResponse> handleServiceValidation(ValidationException ex) {
        log.debug("Service-layer validation failed on {} field(s)", ex.getErrors().size());
        return badRequestValidation(ex.getErrors());
    }

    // ------------------------------------------------------------------
    // Fallback (500)
    // ------------------------------------------------------------------

    /** Last resort: unexpected failure. Never leak internals to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "system.error");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> badRequestValidation(List<ValidationError> errors) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation(VALIDATION_FAILED,
                        messageHelper.get(VALIDATION_FAILED), errors));
    }

    /** Resolves {@code code} (+ args) and wraps it as the whole error body. */
    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, Object... args) {
        return simple(status, code, messageHelper.get(code, args));
    }

    /** 4xx contract violation: {@code warn} without stack trace, localized body. */
    private ResponseEntity<ErrorResponse> clientError(HttpStatus status, String code, Exception ex) {
        log.warn("Contract violation {} on {}: {}", ex.getClass().getSimpleName(), status, code);
        return error(status, code);
    }

    /** Wraps an already localized message as the whole error body. */
    private static ResponseEntity<ErrorResponse> simple(HttpStatus status, String code, String localizedMessage) {
        return ResponseEntity.status(status).body(ErrorResponse.simple(code, localizedMessage));
    }

    /** Walks the cause chain of an unreadable body looking for a Jackson value-format problem. */
    private static InvalidFormatException findInvalidFormat(HttpMessageNotReadableException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException invalid) {
                return invalid;
            }
            // Jackson 3 may wrap the format problem in a DatabindException.
            if (cause instanceof DatabindException databind
                    && databind.getCause() instanceof InvalidFormatException invalid) {
                return invalid;
            }
        }
        return null;
    }

    /** Duplicate-key detection: SQLState 23505 or vendor message marker. */
    private static boolean isDuplicateKey(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
            return true;
        }
        String message = String.valueOf(cause.getMessage());
        return message.contains("duplicate key") || message.contains("Unique index")
                || message.contains("unique constraint") || message.contains("already exists");
    }
}
