package com.gepe.starter.platform.web.response;

/**
 * A single field-level validation problem.
 *
 * @param field name of the invalid field
 * @param message localized problem description
 */
public record ValidationError(String field, String message) {
}
