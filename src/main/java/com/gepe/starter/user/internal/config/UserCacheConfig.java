package com.gepe.starter.user.internal.config;

import java.time.Duration;

import com.gepe.starter.platform.config.CacheSpec;
import com.gepe.starter.user.api.dto.UserResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache declarations of the {@code user} module (see {@code agents.md} §3).
 *
 * <p>The platform {@link CacheSpec}-collecting {@code CacheManager} turns each
 * {@link CacheSpec} bean below into a configured Redis cache (TTL, typed JSON
 * serializer, key prefix). Modules only declare <em>what</em> they cache;
 * never configure a cache manager or serializer yourself.
 *
 * <p>Cache name constants live next to their spec and are referenced from the
 * {@code @Cacheable}/{@code @CacheEvict} annotations of the module service —
 * one name, one value type, declared once.
 */
@Configuration(proxyBeanMethods = false)
public class UserCacheConfig {

    /** Single-user read cache: {@code UserApi.getUser(id)} (values: {@link UserResponse}). */
    public static final String USERS_BY_ID = "users-by-id";

    /**
     * 10 minutes: explicit eviction on create keeps entries fresh; the TTL is
     * only the cross-instance safety net (see {@code agents.md} §11.2).
     */
    @Bean
    CacheSpec usersByIdCacheSpec() {
        return CacheSpec.single(USERS_BY_ID, Duration.ofMinutes(10), UserResponse.class);
    }
}
