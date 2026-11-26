package com.hierarchyhub.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RedisCache {

    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public RedisCache(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    public boolean hasKey(String key) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            return Boolean.TRUE.equals(connection.exists(bytes(key)));
        }
    }

    /** The cached value under {@code key}, deserialized as a list of maps, or {@code null} if absent or unreadable. */
    public List<Map<String, Object>> getList(String key) {
        return get(key, new TypeReference<List<Map<String, Object>>>() {
        });
    }

    /**
     * The cached value under {@code key}, deserialized as {@code typeReference}, or {@code null} if absent,
     * expired, or unreadable - a cache-read failure is always treated as a miss, never surfaced to the caller.
     */
    public <T> T get(String key, TypeReference<T> typeReference) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            byte[] value = connection.get(bytes(key));
            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, typeReference);
        } catch (Exception e) {
            return null;
        }
    }

    /** Caches {@code value} under {@code key} for {@code timeout} {@code unit} - expiry alone keeps this fresh, no eviction call required on every write path. */
    public void setWithTtl(String key, Object value, long timeout, TimeUnit unit) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            byte[] serialized = objectMapper.writeValueAsBytes(value);
            connection.set(bytes(key), serialized, Expiration.from(timeout, unit), SetOption.upsert());
        } catch (Exception e) {
            // a cache write failing should never fail the request it's caching for
        }
    }

    /** Evicts {@code key} - call this right after a write that makes the cached value stale, so the next read doesn't wait out the TTL to see it. */
    public void delete(String key) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.del(bytes(key));
        } catch (Exception e) {
            // a cache eviction failing should never fail the write it's cleaning up after
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
