package com.gepe.starter.platform.config;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Declaration of one application cache (see §3 of {@code agents.md}).
 *
 * <p>Every cache is declared exactly once by the module that owns it, as a
 * {@code CacheSpec} bean in that module's {@code internal} code. The platform
 * {@link CacheConfig} collects all specs and configures the shared Redis-backed
 * {@code CacheManager} from them — the cache <em>mechanics</em> (manager,
 * key prefix, serializers, TTL) live once in {@code platform/config}, never per
 * module. An unknown cache name (typo in {@code @Cacheable}) then fails fast
 * instead of silently creating an unconfigured cache.
 *
 * <p>Rules (enforced at startup by {@link CacheConfig}):
 * <ul>
 *   <li>the cache value type must be an immutable API DTO {@code record} of
 *       the owning module (never an entity) — the rule "cache only read paths
 *       returning api DTO records" of {@code agents.md} §3;</li>
 *   <li>one cache name holds exactly one value type; differently typed data
 *       needs a differently named cache;</li>
 *   <li>collections are declared with {@link #list(String, Duration, Class)}
 *       and are read back as {@link List}.</li>
 * </ul>
 *
 * <p>TTL is chosen by the owning module (how stale may this data be?); the
 * cache is a safety net on top of explicit eviction — mutating operations
 * evict their caches, see {@code agents.md} §11.2.
 *
 * @param cacheName  logical cache name used in {@code @Cacheable(cacheNames = …)}
 * @param ttl        time-to-live of every entry in this cache
 * @param valueType  value type: the record class for {@code single}, or
 *                   {@code List.class} for {@code list}
 * @param elementType element record class for {@code list} caches, otherwise {@code null}
 */
public record CacheSpec(String cacheName, Duration ttl, Class<?> valueType, Class<?> elementType) {

    public CacheSpec {
        Objects.requireNonNull(cacheName, "cacheName must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        Objects.requireNonNull(valueType, "valueType must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cache '%s': ttl must be positive, was %s".formatted(cacheName, ttl));
        }
    }

    /**
     * Declares a cache whose values are single DTO records of
     * {@code valueType} (e.g. {@code getUser(id)} read paths).
     */
    public static CacheSpec single(String cacheName, Duration ttl, Class<?> valueType) {
        return new CacheSpec(cacheName, ttl, valueType, null);
    }

    /**
     * Declares a cache whose values are {@link List}s of DTO records of
     * {@code elementType} (e.g. paged/list read paths).
     */
    public static CacheSpec list(String cacheName, Duration ttl, Class<?> elementType) {
        Objects.requireNonNull(elementType, "elementType must not be null");
        return new CacheSpec(cacheName, ttl, List.class, elementType);
    }

    /** Whether this spec stores {@link List} values ({@link #list}). */
    public boolean isList() {
        return List.class.equals(valueType);
    }
}
