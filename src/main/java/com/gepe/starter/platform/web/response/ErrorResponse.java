package com.gepe.starter.platform.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Error body produced by the platform {@code GlobalExceptionHandler} for every
 * failed HTTP response (see §6 of {@code agents.md}).
 *
 * <p>Contract:
 * <ul>
 *   <li>{@code code} — stable, machine-readable error identifier. It always
 *       equals the resolved message key (e.g. {@code user.not-found},
 *       {@code validation.failed}) and is what frontends use for branching
 *       logic; never derive logic from {@link #message()}. Message keys are
 *       public API — renaming one is a breaking change.</li>
 *   <li>{@code message} — localized, human-readable headline. Always present;
 *       this is the text frontends render (toast/banner) and must not be
 *       parsed.</li>
 *   <li>{@code errors} — per-field problems, present only for validation
 *       failures (400) so form UIs can bind them to inputs; {@code null}
 *       otherwise.</li>
 * </ul>
 *
 * @param code stable machine-readable error code (= message key)
 * @param message localized headline for humans
 * @param errors per-field validation problems, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<ValidationError> errors) {

    public ErrorResponse {
        errors = (errors == null || errors.isEmpty()) ? null : List.copyOf(errors);
    }

    /** Error without field-level details: {@code code} + localized {@code message}. */
    public static ErrorResponse simple(String code, String localizedMessage) {
        return new ErrorResponse(code, localizedMessage, null);
    }

    /**
     * Validation error: {@code code} and localized headline in {@code message},
     * all field problems in {@code errors}.
     */
    public static ErrorResponse validation(String code, String localizedHeadline, List<ValidationError> fieldProblems) {
        return new ErrorResponse(code, localizedHeadline, fieldProblems);
    }
}
