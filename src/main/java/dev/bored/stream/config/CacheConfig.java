package dev.bored.stream.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Enables Spring's cache abstraction + installs a fail-open error handler
 * so Redis hiccups don't take down the service. Same pattern as
 * profile-service; see that service's CacheConfig for detail.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new FailOpenCacheErrorHandler();
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer jsonRedisCacheManagerCustomizer() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfSubType("dev.bored.")
                                .allowIfSubType("java.util.")
                                .allowIfSubType("java.time.")
                                .build(),
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);
        RedisCacheConfiguration jsonConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(1))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return builder -> builder.cacheDefaults(jsonConfig);
    }

    static final class FailOpenCacheErrorHandler extends SimpleCacheErrorHandler {
        @Override
        public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
            log.warn("Cache get failed for {}[{}] — falling through to loader", cache.getName(), key, ex);
        }

        @Override
        public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
            log.warn("Cache put failed for {}[{}]", cache.getName(), key, ex);
        }

        @Override
        public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
            log.warn("Cache evict failed for {}[{}]", cache.getName(), key, ex);
        }

        @Override
        public void handleCacheClearError(RuntimeException ex, Cache cache) {
            log.warn("Cache clear failed for {}", cache.getName(), ex);
        }
    }
}
