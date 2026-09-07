package com.gepe.starter.platform.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Success body envelope for HTTP responses (see §6 of {@code agents.md}).
 *
 * @param message localized success message (maybe {@code null} when the
 *        endpoint has nothing meaningful to say)
 * @param data response payload, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String message, T data) {
}
