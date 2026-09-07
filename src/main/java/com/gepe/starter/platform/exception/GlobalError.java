package com.gepe.starter.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Shared error codes usable from every module. Keys resolve through the
 * app-wide bundle {@code i18n/messages/messages*.properties}.
 *
 * <p>Module-specific errors must live in their own module-scoped enums
 * implementing {@link ErrorCode} (see §6 of {@code agents.md}); this enum only
 * hosts codes that are genuinely cross-cutting (database conflicts, system
 * failures, …).
 */
@Getter
public enum GlobalError implements ErrorCode {

    // ── database ──
    DB_CONSTRAINT_VIOLATION(HttpStatus.CONFLICT, "db.constraint_violation"),
    DB_DATA_INTEGRITY(HttpStatus.CONFLICT, "db.data_integrity"),
    DB_DUPLICATE_ENTRY(HttpStatus.CONFLICT, "db.duplicate_entry"),

    // ── exception (generic) ──
    EXCEPTION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "exception.access_denied"),
    EXCEPTION_ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "exception.entity_not_found"),
    EXCEPTION_ILLEGAL_ARGUMENT(HttpStatus.BAD_REQUEST, "exception.illegal_argument"),
    EXCEPTION_ILLEGAL_STATE(HttpStatus.CONFLICT, "exception.illegal_state"),
    EXCEPTION_OPTIMISTIC_LOCK(HttpStatus.CONFLICT, "exception.optimistic_lock"),

    // ── file ──
    FILE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "file.invalid_type"),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "file.not_found"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "file.too_large"),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "file.upload_failed"),

    // ── http ──
    HTTP_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "http.too_many_attempts"),

    // ── system ──
    SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "system.error"),
    SYSTEM_MAINTENANCE(HttpStatus.SERVICE_UNAVAILABLE, "system.maintenance"),
    SYSTEM_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "system.timeout"),
    SYSTEM_REQUEST_IN_PROGRESS(HttpStatus.SERVICE_UNAVAILABLE, "system.request_in_progress"),

    // ── validation ──
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "validation.failed");

    private final HttpStatus httpStatus;
    private final String messageKey;

    GlobalError(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
