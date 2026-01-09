package com.shop.ecommerceengine.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache configuration for Redis-backed caching.
 * Configures cache names, TTLs, and serialization.
 * Uses JDK serialization for compatibility with Spring Data Page<T> and other complex types.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Default TTL for cache entries (10 minutes).
     */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * TTL for products cache (10 minutes).
     */
    private static final Duration PRODUCTS_TTL = Duration.ofMinutes(10);

    /**
     * TTL for categories cache (30 minutes - changes less frequently).
     */
    private static final Duration CATEGORIES_TTL = Duration.ofMinutes(30);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Use JDK serialization for value serialization - handles Spring Data Page<T> and complex types
        JdkSerializationRedisSerializer jdkSerializer = new JdkSerializationRedisSerializer();

        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jdkSerializer))
                .disableCachingNullValues();

        // Per-cache configurations with custom TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Products cache with 10-minute TTL
        cacheConfigurations.put("products", defaultConfig.entryTtl(PRODUCTS_TTL));

        // Categories cache with 30-minute TTL (changes less frequently)
        cacheConfigurations.put("categories", defaultConfig.entryTtl(CATEGORIES_TTL));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
