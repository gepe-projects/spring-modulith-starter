/**
 * Named interface {@code API} of the {@code user} module (see {@code agents.md}
 * §2.1): the only subtree other modules may reference, declared via
 * {@code allowedDependencies = "user::API"}. It is a pure contract — no
 * {@code internal}, JPA or web types — so it must never depend on
 * {@code com.gepe.starter.user.internal}.
 */
@org.springframework.modulith.NamedInterface("API")
package com.gepe.starter.user.api;
