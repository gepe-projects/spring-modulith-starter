package com.gepe.starter.user.internal.delivery.http.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HTTP request body of {@code POST /api/v1/users} (see {@code agents.md} §3):
 * bean validation lives here, on the web layer — never on the api DTOs.
 *
 * <p>Message keys reference the module bundle {@code i18n/user/} through the
 * aggregated MessageSource ({@code agents.md} §6), so they localize with the
 * request locale like every other text.
 *
 * @param name display name of the user
 * @param email email of the user (must be unique)
 */
public record CreateUserRequest(
        @NotBlank(message = "{user.validation.name.required}")
        @Size(max = 100)
        String name,

        @NotBlank(message = "{user.validation.email.required}")
        @Email(message = "{user.validation.email.invalid}")
        @Size(max = 320)
        String email) {
}
