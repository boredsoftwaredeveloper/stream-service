package dev.bored.stream.service;

/**
 * Cache names used by {@code @Cacheable} / {@code @CacheEvict} annotations
 * across stream-service. Centralised so the Redis key prefix + TTL config
 * in application.yml can reference the same strings.
 */
public final class CacheNames {
    public static final String FEED_POSTS_ALL = "feed-posts-all";
    public static final String FEED_POST_BY_ID = "feed-post-by-id";

    private CacheNames() { }
}
