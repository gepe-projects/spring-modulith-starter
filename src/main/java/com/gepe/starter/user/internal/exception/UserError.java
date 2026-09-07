package com.gepe.starter.user.internal.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import com.gepe.starter.platform.exception.ErrorCode;

/**
 * Error codes owned by the {@code user} module (see {@code agents.md} §6):
 * one constant = HTTP status + message key resolved through the aggregated
 * MessageSource. Keys live in the module's own bundle
 * {@code i18n/user/messages*.properties} and are prefixed with {@code user.}.
 *
 * <p>Only genuinely cross-cutting codes go to
 * {@link com.gepe.starter.platform.exception.GlobalError}; add module-specific
 * codes here as the module grows, e.g.
 * {@code EMAIL_TAKEN(HttpStatus.CONFLICT, "user.email_taken")}.
 */
@Getter
public enum UserError implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "user.not-found");

    private final HttpStatus httpStatus;
    private final String messageKey;

    UserError(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
