package com.wjx871.population.system;

import java.util.ArrayDeque;
import org.springframework.stereotype.Component;

/** Bounded in-process request window. No request paths, identities or payloads retained. */
@Component
public class RequestMetrics {
    private static final int CAPACITY = 4096;
    private static final long WINDOW_NANOS = 60_000_000_000L;
    private final ArrayDeque<Sample> recent = new ArrayDeque<>();
    private long total;
    private long totalServerErrors;
    private long lastCapacityDrop;

    public synchronized void record(long elapsedNanos, int status) {
        long now = System.nanoTime();
        expire(now);
        total++;
        if (status >= 500) totalServerErrors++;
        if (recent.size() == CAPACITY) { recent.removeFirst(); lastCapacityDrop = now; }
        recent.addLast(new Sample(now, elapsedNanos / 1_000_000.0, status));
    }
    public synchronized Snapshot snapshot() {
        long now = System.nanoTime();
        expire(now);
        double[] times = recent.stream().mapToDouble(Sample::milliseconds).sorted().toArray();
        long errors = recent.stream().filter(sample -> sample.status() >= 500).count();
        return new Snapshot(total, totalServerErrors, 60, CAPACITY, times.length,
                lastCapacityDrop != 0 && now - lastCapacityDrop <= WINDOW_NANOS,
                times.length == 0 ? null : percentile(times, .50),
                times.length == 0 ? null : percentile(times, .95),
                times.length == 0 ? null : errors / (double) times.length, "nearest_rank");
    }
    private void expire(long now) {
        while (!recent.isEmpty() && now - recent.peekFirst().at() > WINDOW_NANOS) recent.removeFirst();
    }
    private double percentile(double[] times, double quantile) { return times[(int) Math.ceil(times.length * quantile) - 1]; }
    private record Sample(long at, double milliseconds, int status) {}
    public record Snapshot(long totalRequests, long totalServerErrors, int windowSeconds, int capacity,
            int samples, boolean capacityLimited, Double p50Ms, Double p95Ms, Double serverErrorRate, String percentileMethod) {}
}
