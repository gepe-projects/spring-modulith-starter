package com.gepe.starter.user.internal.entity;

import java.util.UUID;

import com.github.f4b6a3.uuid.UuidCreator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * User aggregate root (see {@code agents.md} §3): plain JPA entity, no
 * business logic beyond creation.
 *
 * <p>The id is a UUID v7 generated in the application
 * ({@code UuidCreator.getTimeOrderedEpoch()}) — never a database-generated
 * value. The unique {@code email} constraint lives in the database (Flyway
 * migration {@code V3}), which is the cross-instance-safe guard for
 * duplicates (see {@code UserApi} and {@code agents.md} §11).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor // JPA
public class User {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    /** Creates a new user with an application-generated UUID v7 id. */
    public static User create(String name, String email) {
        User user = new User();
        user.id = UuidCreator.getTimeOrderedEpoch();
        user.name = name;
        user.email = email;
        return user;
    }
}
