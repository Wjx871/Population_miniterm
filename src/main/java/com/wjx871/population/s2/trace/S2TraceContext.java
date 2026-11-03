package com.wjx871.population.s2.trace;

import org.apache.logging.log4j.ThreadContext;

import java.util.UUID;
import java.util.regex.Pattern;

public final class S2TraceContext {

    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String INSTANCE_ID = resolveInstanceId();

    private S2TraceContext() {
    }

    public static String resolveTraceId(String candidate) {
        return candidate != null && SAFE_TRACE_ID.matcher(candidate).matches()
                ? candidate : UUID.randomUUID().toString();
    }

    public static String instanceId() {
        return INSTANCE_ID;
    }

    public static Scope open(String traceId) {
        String previousTraceId = ThreadContext.get("traceId");
        String previousInstanceId = ThreadContext.get("instanceId");
        ThreadContext.put("traceId", resolveTraceId(traceId));
        ThreadContext.put("instanceId", INSTANCE_ID);
        return new Scope(previousTraceId, previousInstanceId);
    }

    private static String resolveInstanceId() {
        String configured = System.getProperty("s2.instanceId");
        if (configured != null && SAFE_TRACE_ID.matcher(configured).matches()) {
            return configured;
        }
        return UUID.randomUUID().toString();
    }

    public static final class Scope implements AutoCloseable {
        private final String previousTraceId;
        private final String previousInstanceId;

        private Scope(String previousTraceId, String previousInstanceId) {
            this.previousTraceId = previousTraceId;
            this.previousInstanceId = previousInstanceId;
        }

        @Override
        public void close() {
            restore("traceId", previousTraceId);
            restore("instanceId", previousInstanceId);
        }

        private void restore(String key, String value) {
            if (value == null) {
                ThreadContext.remove(key);
            } else {
                ThreadContext.put(key, value);
            }
        }
    }
}
