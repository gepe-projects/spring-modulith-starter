package com.gepe.starter.user.api;

import java.util.List;
import java.util.UUID;

import com.gepe.starter.user.api.dto.CreateUserCommand;
import com.gepe.starter.user.api.dto.UserResponse;

/**
 * Facade of the {@code user} module (see {@code agents.md} §2): the only
 * entry point for this module's own HTTP layer and for other modules
 * (declared as {@code allowedDependencies = "user::API"}).
 *
 * <p>Implementation lives in {@code com.gepe.starter.user.internal.service}.
 * Transactions belong to the implementations of these methods, never to the
 * controllers or repositories ({@code agents.md} §3).
 */
public interface UserApi {

    /**
     * Creates a user from the given command. The id is generated in the
     * application as a UUID v7 (see {@code agents.md} §3). A duplicate
     * {@code email} is rejected by the database unique constraint and surfaces
     * as HTTP 409 {@code db.duplicate_entry} through the platform
     * {@code GlobalExceptionHandler} — this stays correct even when two
     * instances create the same email concurrently (multi-instance, see
     * {@code agents.md} §11).
     *
     * @param command validated command (name, email)
     * @return the created user; never {@code null}
     */
    UserResponse createUser(CreateUserCommand command);

    /**
     * Returns the user with the given id.
     *
     * @param id UUID v7 of the user
     * @return the user
     * @throws com.gepe.starter.platform.exception.ServiceException with
     *         {@code UserError.USER_NOT_FOUND} when no user exists for the id
     */
    UserResponse getUser(UUID id);

    /**
     * Returns all users ordered by name.
     *
     * @return never {@code null}; possibly empty
     */
    List<UserResponse> getUsers();
}
