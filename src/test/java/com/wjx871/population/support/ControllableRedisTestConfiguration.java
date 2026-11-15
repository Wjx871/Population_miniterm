package com.wjx871.population.support;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Replaces only the Redis transport; production cache serialization and fallback remain real. */
@TestConfiguration
public class ControllableRedisTestConfiguration {
    public record Observation(String operation, boolean active, boolean readOnly) {}

    public static class Store {
        public final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();
        public final AtomicInteger gets = new AtomicInteger();
        public final AtomicInteger puts = new AtomicInteger();
        public final AtomicInteger evictions = new AtomicInteger();
        public final List<Observation> observations = new CopyOnWriteArrayList<>();
        public volatile boolean failGet, failSet, failScan, failDelete;

        public void observe(String operation) {
            observations.add(new Observation(operation,
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly()));
        }

        public void reset() {
            values.clear(); gets.set(0); puts.set(0); evictions.set(0); observations.clear();
            failGet = failSet = failScan = failDelete = false;
        }
    }

    @Bean public Store controlledRedisStore() { return new Store(); }

    @Bean @Primary @SuppressWarnings("unchecked")
    public StringRedisTemplate controlledRedisTemplate(Store store) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(call -> {
            store.observe("GET"); store.gets.incrementAndGet();
            if (store.failGet) throw new IllegalStateException("synthetic Redis GET failure");
            return store.values.get(call.getArgument(0));
        });
        doAnswer(call -> {
            store.observe("SET"); store.puts.incrementAndGet();
            if (store.failSet) throw new IllegalStateException("synthetic Redis SET failure");
            store.values.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        when(redis.scan(any(ScanOptions.class))).thenAnswer(call -> {
            store.observe("SCAN");
            if (store.failScan) throw new IllegalStateException("synthetic Redis SCAN failure");
            ScanOptions options = call.getArgument(0);
            String pattern = options.getPattern();
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            var keys = store.values.keySet().stream().filter(key -> pattern.endsWith("*")
                    ? key.startsWith(prefix) : key.equals(prefix)).toList().iterator();
            Cursor<String> cursor = mock(Cursor.class);
            when(cursor.hasNext()).thenAnswer(ignored -> keys.hasNext());
            when(cursor.next()).thenAnswer(ignored -> keys.next());
            return cursor;
        });
        when(redis.delete(anyCollection())).thenAnswer(call -> {
            store.observe("DELETE"); store.evictions.incrementAndGet();
            if (store.failDelete) throw new IllegalStateException("synthetic Redis DELETE failure");
            Collection<String> keys = call.getArgument(0);
            return keys.stream().filter(k -> store.values.remove(k) != null).count();
        });
        when(redis.delete(anyString())).thenAnswer(call -> {
            store.observe("DELETE"); store.evictions.incrementAndGet();
            if (store.failDelete) throw new IllegalStateException("synthetic Redis DELETE failure");
            return store.values.remove(call.getArgument(0)) != null;
        });
        return redis;
    }
}
