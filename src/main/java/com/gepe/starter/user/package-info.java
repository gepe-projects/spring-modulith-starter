/**
 * Example feature module (see {@code agents.md} §2, §9) — the reference
 * implementation to copy when adding the next module.
 *
 * <p>It is intentionally small (create + read a {@code user}) but complete:
 * it demonstrates the full {@code api}/{@code internal} split, the module
 * error enum ({@code internal/exception}), module-owned i18n keys
 * ({@code i18n/user/}), request validation on the HTTP DTO, the
 * {@code ApiResponse}/{@code ErrorResponse} envelopes, parameterized
 * {@code @Slf4j} logging, and a domain event published to the Modulith event
 * publication registry ({@code api/event} + {@code internal/listener}).
 *
 * <p>Rules applied here — CLOSED module whose only public surface is the
 * named interface {@code API}; {@code allowedDependencies} stays empty because
 * the module only uses the shared {@code platform} module, which is implicitly
 * allowed via {@code @Modulith(sharedModules = "platform")} and therefore
 * never listed here (see {@code agents.md} §2.3/§2.4).
 */
@org.springframework.modulith.ApplicationModule(
        id = "user",
        allowedDependencies = {"platform"} // only shared modules (platform) are allowed, implicitly
)
package com.gepe.starter.user;
