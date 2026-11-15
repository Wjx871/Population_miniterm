package com.wjx871.population.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.data.redis.core.ScanOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
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
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder readErrors = new LongAdder();
    private final LongAdder writeErrors = new LongAdder();
    private final LongAdder evictionErrors = new LongAdder();

    public boolean enabled() { return properties.isEnabled(); }
    public String key(String suffix) { return properties.getKeyPrefix() + ":" + suffix; }

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        if (!enabled()) return Optional.empty();
        try {
            String value = redis.opsForValue().get(key);
            return recordRead(value == null ? null : json.readValue(value, type));
        } catch (RuntimeException | JsonProcessingException ex) {
            readErrors.increment();
            warn(ex); return Optional.empty();
        }
    }
    public <T> Optional<T> get(String key, JavaType type) {
        if (!enabled()) return Optional.empty();
        try {
            String value = redis.opsForValue().get(key);
            return recordRead(value == null ? null : json.readValue(value, type));
        } catch (RuntimeException | JsonProcessingException ex) {
            readErrors.increment();
            warn(ex); return Optional.empty();
        }
    }
    public void put(String key, Object value, Duration ttl) {
        if (!enabled()) return;
        try { redis.opsForValue().set(key, json.writeValueAsString(value), ttl); }
        catch (RuntimeException | JsonProcessingException ex) { writeErrors.increment(); warn(ex); }
    }
    public boolean contains(String key) {
        if (!enabled()) return false;
        try { return Boolean.TRUE.equals(redis.hasKey(key)); }
        catch (RuntimeException ex) { warn(ex); return false; }
    }
    public void putMarker(String key, Duration ttl) {
        if (!enabled()) return;
        try { redis.opsForValue().set(key, "1", ttl); }
        catch (RuntimeException ex) { writeErrors.increment(); warn(ex); }
    }
    /** A false result means confirmed absence, never an unavailable store. */
    public boolean strictContains(String key) {
        requireEnabled();
        try {
            Boolean exists = redis.hasKey(key);
            if (exists == null) throw new StrictRedisException("UNCONFIRMED", null);
            return exists;
        } catch (StrictRedisException ex) { throw ex; }
        catch (RuntimeException ex) { throw strictFailure(ex); }
    }

    /** Atomic SET + absolute expiry; normal completion means a confirmed write. */
    public void strictPutMarker(String key, Instant expiresAt) {
        requireEnabled();
        try {
            byte[] encodedKey = redis.getStringSerializer().serialize(key);
            byte[] marker = redis.getStringSerializer().serialize("1");
            Boolean confirmed = redis.execute((RedisCallback<Boolean>) connection ->
                    connection.stringCommands().set(encodedKey, marker,
                            Expiration.unixTimestamp(expiresAt.toEpochMilli(), TimeUnit.MILLISECONDS),
                            SetOption.UPSERT));
            if (!Boolean.TRUE.equals(confirmed)) throw new StrictRedisException("UNCONFIRMED", null);
        } catch (StrictRedisException ex) { throw ex; }
        catch (RuntimeException ex) { throw strictFailure(ex); }
    }

    private void requireEnabled() {
        if (!enabled()) throw new StrictRedisException("DISABLED", null);
    }

    private StrictRedisException strictFailure(RuntimeException ex) {
        String category = "STORE_ERROR";
        Throwable current = ex;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            String type = current.getClass().getSimpleName();
            if (type.contains("Timeout")) { category = "TIMEOUT"; break; }
            if (type.contains("Authentication") || type.contains("Permission") || type.contains("AccessDenied")) {
                category = "AUTHENTICATION"; break;
            }
            if (type.contains("Connection")) category = "CONNECTION";
        }
        return new StrictRedisException(category, ex);
    }
    public void evict(String... keys) {
        if (!enabled()) return;
        for (String key : keys) try { redis.delete(key); } catch (RuntimeException ex) { evictionErrors.increment(); warn(ex); }
    }
    public void evictPattern(String pattern) {
        if (!enabled()) return;
        // SCAN avoids a blocking KEYS call. This remains best-effort invalidation,
        // not a strong-consistency guarantee against concurrent cache writers.
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(256).build())) {
            var batch = new ArrayList<String>(128);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == 128) { redis.delete(batch); batch.clear(); }
            }
            if (!batch.isEmpty()) redis.delete(batch);
        } catch (RuntimeException ex) { evictionErrors.increment(); warn(ex); }
    }
    public boolean ping() {
        if (!enabled()) return false;
        try (var connection = redis.getConnectionFactory().getConnection()) {
            return connection != null && "PONG".equalsIgnoreCase(connection.ping());
        }
        catch (RuntimeException ex) { warn(ex); return false; }
    }
    private <T> Optional<T> recordRead(T value) {
        if (value == null) { misses.increment(); return Optional.empty(); }
        hits.increment();
        return Optional.of(value);
    }
    /** Per-process cumulative counters since startup; disabled reads are excluded. */
    public CacheSnapshot snapshot() {
        return new CacheSnapshot(hits.sum(), misses.sum(), readErrors.sum(), writeErrors.sum(), evictionErrors.sum());
    }
    public record CacheSnapshot(long hits, long misses, long readErrors, long writeErrors, long evictionErrors) {}
    private void warn(Exception ex) {
        long now = System.currentTimeMillis(), previous = lastWarning.get();
        if (now - previous >= WARNING_INTERVAL_MS && lastWarning.compareAndSet(previous, now)) {
            log.warn("Redis unavailable; falling back to MySQL/local mode");
        }
    }
}
