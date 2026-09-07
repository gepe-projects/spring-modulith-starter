package com.gepe.starter.user.api.dto;

import java.util.UUID;

/**
 * Use-case output of the {@code user} module (see {@code agents.md} §2.1):
 * immutable, JPA-free, safe for other modules and for HTTP responses alike.
 *
 * @param id UUID v7 of the user
 * @param name user display name
 * @param email user email
 */
public record UserResponse(UUID id, String name, String email) {
}
