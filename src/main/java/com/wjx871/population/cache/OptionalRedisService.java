package com.wjx871.population.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class OptionalRedisService {
    private static final long WARNING_INTERVAL_MS = 60_000;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final RedisProperties properties;
    private final AtomicLong lastWarning = new AtomicLong();

    public boolean enabled() { return properties.isEnabled(); }
    public String key(String suffix) { return properties.getKeyPrefix() + ":" + suffix; }

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        if (!enabled()) return Optional.empty();
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, type));
        } catch (RuntimeException | JsonProcessingException ex) {
            warn(ex); return Optional.empty();
        }
    }
    public void put(String key, Object value, Duration ttl) {
        if (!enabled()) return;
        try { redis.opsForValue().set(key, json.writeValueAsString(value), ttl); }
        catch (RuntimeException | JsonProcessingException ex) { warn(ex); }
    }
    public boolean contains(String key) {
        if (!enabled()) return false;
        try { return Boolean.TRUE.equals(redis.hasKey(key)); }
        catch (RuntimeException ex) { warn(ex); return false; }
    }
    public void putMarker(String key, Duration ttl) {
        if (!enabled()) return;
        try { redis.opsForValue().set(key, "1", ttl); }
        catch (RuntimeException ex) { warn(ex); }
    }
    public void evict(String... keys) {
        if (!enabled()) return;
        for (String key : keys) try { redis.delete(key); } catch (RuntimeException ex) { warn(ex); }
    }
    public boolean ping() {
        if (!enabled()) return false;
        try { return "PONG".equalsIgnoreCase(redis.getConnectionFactory().getConnection().ping()); }
        catch (RuntimeException ex) { warn(ex); return false; }
    }
    private void warn(Exception ex) {
        long now = System.currentTimeMillis(), previous = lastWarning.get();
        if (now - previous >= WARNING_INTERVAL_MS && lastWarning.compareAndSet(previous, now)) {
            log.warn("Redis unavailable; falling back to MySQL/local mode: {}", ex.getMessage());
        }
    }
}
