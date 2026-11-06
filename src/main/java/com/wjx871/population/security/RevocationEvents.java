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
    private final ConcurrentHashMap<String, AtomicLong> lastWarning = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();

    public void record(String event, String operation, RevocationProperties.Mode mode,
                       String category, long startedNanos) {
        counts.computeIfAbsent(event, ignored -> new LongAdder()).increment();
        boolean failure = event.endsWith("UNAVAILABLE");
        if (failure) {
            AtomicLong last = lastWarning.computeIfAbsent(event + operation, ignored -> new AtomicLong());
            long now = System.nanoTime(), previous = last.get();
            if (previous != 0 && now - previous < 60_000_000_000L) return;
            if (!last.compareAndSet(previous, now)) return;
        }
        String trace = S2TraceContext.resolveTraceId(ThreadContext.get("traceId"));
        String message = "event={} traceId={} instanceId={} operation={} mode={} errorCategory={} elapsedMs={} count={}";
        Object[] fields = {event, trace, S2TraceContext.instanceId(), operation, mode, category,
                (System.nanoTime() - startedNanos) / 1_000_000, count(event)};
        if (failure) log.warn(message, fields); else log.info(message, fields);
    }

    public long count(String event) {
        LongAdder counter = counts.get(event);
        return counter == null ? 0 : counter.sum();
    }
}
