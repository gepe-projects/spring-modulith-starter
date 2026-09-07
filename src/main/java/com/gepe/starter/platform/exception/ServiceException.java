package com.gepe.starter.platform.exception;

import lombok.Getter;

/**
 * The one application exception (see §6 of {@code agents.md}).
 *
 * <p>Modules throw it with one of their own {@link ErrorCode} constants and
 * optional arguments, e.g.
 * {@code throw new ServiceException(UserError.USER_NOT_FOUND, userId);} The
 * {@link GlobalExceptionHandler} maps the HTTP status from the error code and
 * resolves its message key (plus arguments) through the aggregated
 * MessageSource, so neither the throwing module nor the platform hard-codes
 * any client-visible text.
 *
 * <p>Use {@link #getMessage()} only for logs; never render it to clients.
 */
@Getter
public class ServiceException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object[] args;

    public ServiceException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.args = args;
    }

}
