package com.gepe.starter.user.api.dto;

/**
 * Use-case input of {@code UserApi.createUser(…)} (see {@code agents.md} §2.1):
 * the inter-module contract between the HTTP layer and the facade. Validation
 * lives on the HTTP request DTO ({@code internal/delivery/http/req}), not here.
 *
 * @param name user display name
 * @param email user email (unique)
 */
public record CreateUserCommand(String name, String email) {
}
