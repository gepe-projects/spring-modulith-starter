package com.gepe.starter.platform.logging;

/**
 * Central SLF4J MDC keys for the application (see §7 of {@code agents.md}).
 *
 * <p>Framework-level code (e.g. {@code CorrelationIdFilter}) writes these keys;
 * business code only reads them. Never write to the MDC without clearing it
 * afterwards.
 */
public final class MdcKeys {

    /** Correlation id of the current HTTP request. */
    public static final String REQUEST_ID = "requestId";

    /** Id of the authenticated user (planned — populated once authentication exists). */
    public static final String USER_ID = "userId";

    /** Logical module that produced the log line (planned). */
    public static final String MODULE = "module";

    private MdcKeys() {
        // constants holder
    }
}
