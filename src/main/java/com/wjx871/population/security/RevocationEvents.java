package com.wjx871.population.security;

import com.wjx871.population.s2.trace.S2TraceContext;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.stereotype.Component;

/** Bounded event categories, no keys, exception messages, or credentials. */
@Log4j2
@Component
public class RevocationEvents {
    public static final String REVOCATION_CONFIRMED = "REVOCATION_CONFIRMED";
    public static final String REVOCATION_REJECTED = "REVOCATION_REJECTED";
    public static final String REVOCATION_READ_UNAVAILABLE = "REVOCATION_READ_UNAVAILABLE";
    public static final String REVOCATION_WRITE_UNAVAILABLE = "REVOCATION_WRITE_UNAVAILABLE";

    public static final String REVOCATION_DB_CONFIRMED = "REVOCATION_DB_CONFIRMED";
    public static final String REVOCATION_DB_UNAVAILABLE = "REVOCATION_DB_UNAVAILABLE";
    public static final String REVOCATION_REJECTED_DB = "REVOCATION_REJECTED_DB";
    public static final String REVOCATION_REJECTED_CUTOFF = "REVOCATION_REJECTED_CUTOFF";
    public static final String REVOCATION_CACHE_WRITE_UNAVAILABLE = "REVOCATION_CACHE_WRITE_UNAVAILABLE";
    public static final String REVOCATION_CACHE_READ_UNAVAILABLE = "REVOCATION_CACHE_READ_UNAVAILABLE";
    public static final String REVOCATION_REJECTED_CACHE = "REVOCATION_REJECTED_CACHE";
    public static final String REVOCATION_CLEANUP_BATCH = "REVOCATION_CLEANUP_BATCH";

    private final ConcurrentHashMap<String, AtomicLong> lastWarning = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();

    public void record(String event, String operation, RevocationProperties.Mode mode,
                       String category, long startedNanos) {
        String safeEvent = sanitizeToken(event, "REVOCATION_UNKNOWN_EVENT");
        String safeOperation = sanitizeToken(operation, "UNKNOWN");
        String safeCategory = sanitizeToken(category, "STORE_ERROR");
        counts.computeIfAbsent(safeEvent, ignored -> new LongAdder()).increment();
        boolean failure = safeEvent.endsWith("UNAVAILABLE");
        if (failure) {
            AtomicLong last = lastWarning.computeIfAbsent(safeEvent + ":" + safeOperation, ignored -> new AtomicLong());
            long now = System.nanoTime(), previous = last.get();
            if (previous != 0 && now - previous < 60_000_000_000L) return;
            if (!last.compareAndSet(previous, now)) return;
        }
        String trace = S2TraceContext.resolveTraceId(ThreadContext.get("traceId"));
        String message = "event={} traceId={} instanceId={} operation={} mode={} errorCategory={} elapsedMs={} count={}";
        Object[] fields = {safeEvent, trace, S2TraceContext.instanceId(), safeOperation, mode, safeCategory,
                Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000), count(safeEvent)};
        if (failure) log.warn(message, fields); else log.info(message, fields);
    }

    public long count(String event) {
        if (event == null) {
            return 0;
        }
        LongAdder counter = counts.get(event);
        return counter == null ? 0 : counter.sum();
    }

    private static String sanitizeToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 64 && trimmed.matches("[A-Za-z0-9._:-]+")) {
            return trimmed;
        }
        return fallback;
    }
}
