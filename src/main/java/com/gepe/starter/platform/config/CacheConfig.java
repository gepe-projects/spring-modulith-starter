package com.gepe.starter.platform.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Cache infrastructure of the {@code platform} module (see §3 and §11.2 of
 * {@code agents.md}) — the <strong>only</strong> place where the
 * {@link CacheManager} and its serializers are configured.
 *
 * <p>Store is the shared Redis (never an in-process cache): every instance
 * serves the same entries, which is what makes {@code @Cacheable} safe in a
 * multi-instance deployment. Values are JSON (Jackson 3) serialized with a
 * <em>typed</em> serializer per cache — the type comes from the module-owned
 * {@link CacheSpec} declaration, so records round-trip without
 * {@code @class} type metadata and a cache can only ever hold its declared
 * value type.
 *
 * <p>Operating rules wired here:
 * <ul>
 *   <li><b>fail fast on unknown caches</b>: {@code @Cacheable} with a cache
 *       name that has no {@link CacheSpec} throws instead of silently creating
 *       an unconfigured cache ({@code disableCreateOnMissingCache});</li>
 *   <li><b>transaction-aware</b>: evictions and puts run only after the
 *       surrounding transaction committed, so a rolled-back write never
 *       evicts a still-valid cache;</li>
 *   <li><b>key prefix</b> {@code <spring.application.name>::} keeps a shared
 *       Redis clean when several applications use the same instance;</li>
 *   <li><b>statistics</b> enabled for the actuator cache metrics once metrics
 *       are exposed (§7 of {@code agents.md}).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    /** Safety-net TTL when a {@link CacheSpec} does not choose its own. */
    static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    @Bean
    CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
            @Value("${spring.application.name:app}") String applicationName, List<CacheSpec> cacheSpecs) {

        validate(cacheSpecs);

        // Own mapper copy: the cache JSON format must not silently change when
        // the application ObjectMapper is customized elsewhere (Jackson 3:
        // rebuild() clones the configuration into a fresh mapper).
        ObjectMapper redisMapper = objectMapper.rebuild().build();

        Map<String, RedisCacheConfiguration> perCache = cacheSpecs.stream()
                .collect(Collectors.toMap(CacheSpec::cacheName,
                        spec -> baseConfiguration(applicationName).entryTtl(spec.ttl())
                                .serializeValuesWith(SerializationPair.fromSerializer(
                                        new JacksonJsonRedisSerializer<>(redisMapper, javaType(spec, redisMapper))))));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfiguration(applicationName))
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .disableCreateOnMissingCache()
                .enableStatistics()
                .build();
    }

    /** Shared cache defaults: key prefix, TTL safety net, no {@code null} entries. */
    private static RedisCacheConfiguration baseConfiguration(String applicationName) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(applicationName + "::")
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues();
    }

    /** Builds the (de)serialization target type of a spec: {@code elementType} for lists, {@code valueType} otherwise. */
    private static JavaType javaType(CacheSpec spec, ObjectMapper redisMapper) {
        if (spec.isList()) {
            return redisMapper.getTypeFactory()
                    .constructCollectionType(List.class, Objects.requireNonNull(spec.elementType()));
        }
        return redisMapper.getTypeFactory().constructType(spec.valueType());
    }

    /**
     * Startup guards for the {@code CacheSpec} contract: unique cache names and
     * record DTO value types only (the "api DTO records" rule of §3).
     */
    private static void validate(List<CacheSpec> cacheSpecs) {
        Map<String, Long> duplicates = cacheSpecs.stream()
                .collect(Collectors.groupingBy(CacheSpec::cacheName, Collectors.counting()));
        duplicates.forEach((name, count) -> {
            if (count > 1) {
                throw new IllegalStateException(
                        "Cache '%s' is declared %d times; declare each cache name exactly once (CacheSpec)"
                                .formatted(name, count));
            }
        });
        for (CacheSpec spec : cacheSpecs) {
            Class<?> valueType = spec.isList() ? spec.elementType() : spec.valueType();
            if (valueType == null || !valueType.isRecord()) {
                throw new IllegalArgumentException(
                        "Cache '%s': value type must be an api DTO record (see agents.md §3), was %s"
                                .formatted(spec.cacheName(), valueType));
            }
        }
    }
}
