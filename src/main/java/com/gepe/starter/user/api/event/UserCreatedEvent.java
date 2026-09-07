package com.gepe.starter.user.api.event;

import java.util.UUID;

/**
 * Domain event published after a user was created (see {@code agents.md} §3,
 * "inter-module communication").
 *
 * <p>It lives in the module's named interface so any other module can listen
 * to it by declaring {@code allowedDependencies = "user::API"}. Publication is
 * tracked by the Modulith event publication registry: the entry is written in
 * the same transaction as the user insert and marked completed once the
 * listener ran — this is what makes event delivery safe across instances
 * (at-least-once, {@code agents.md} §11).
 *
 * @param userId id of the newly created user
 */
public record UserCreatedEvent(UUID userId) {
}
