package com.gepe.starter.user.internal.repository;

import java.util.UUID;

import com.gepe.starter.user.internal.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository of the {@code user} module (see {@code agents.md}
 * §3) — internal to the module, never referenced from outside.
 *
 * <p>The uniqueness of {@code email} is enforced by the database index from
 * migration {@code V3}, not by an application-level check: application-level
 * pre-checks race across instances, the constraint does not
 * ({@code agents.md} §11).
 */
public interface UserRepository extends JpaRepository<User, UUID> {
}
