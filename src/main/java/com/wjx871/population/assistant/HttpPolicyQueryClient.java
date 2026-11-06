package com.wjx871.population.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.s2.trace.S2TraceContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class HttpPolicyQueryClient implements PolicyQueryClient {
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;
    public static final int MAX_REQUEST_BYTES = 32 * 1024;

    private final PolicyPythonProperties properties;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private volatile Duration cachedConnectTimeout;
    private volatile HttpClient httpClient;

    public HttpPolicyQueryClient(PolicyPythonProperties properties,
                                 ObjectMapper objectMapper,
                                 Environment environment) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Override
    public PolicyPythonContracts.Response query(PolicyPythonContracts.Request request) {
        URI targetUri = resolveAndValidateTargetUri();
        String serviceToken = requireValidServiceToken();
        byte[] payload = serializeRequest(request);

        Duration requestTimeout = properties.effectiveRequestTimeout();
        Duration connectTimeout = properties.effectiveConnectTimeout();
        if (connectTimeout.compareTo(requestTimeout) > 0) {
            connectTimeout = requestTimeout;
        }
        long deadlineNanos = System.nanoTime() + requestTimeout.toNanos();

        HttpRequest httpRequest = HttpRequest.newBuilder(targetUri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + serviceToken)
                .header(S2TraceContext.TRACE_HEADER, request.traceId())
                .header("X-Request-Id", request.requestId())
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        BoundedBodySubscriber bodySubscriber = new BoundedBodySubscriber(MAX_RESPONSE_BYTES, deadlineNanos);
        CompletableFuture<HttpResponse<byte[]>> responseFuture;
        try {
            responseFuture = clientFor(connectTimeout).sendAsync(httpRequest, responseInfo -> {
                if (System.nanoTime() > deadlineNanos) {
                    bodySubscriber.failAndCancel(new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true));
                    return bodySubscriber;
                }
                int status = responseInfo.statusCode();
                if (status != 200) {
                    bodySubscriber.failAndCancel(statusException(status));
                    return bodySubscriber;
                }
                OptionalLong contentLength = responseInfo.headers().firstValueAsLong("Content-Length");
                if (contentLength.isPresent() && contentLength.getAsLong() > MAX_RESPONSE_BYTES) {
                    bodySubscriber.failAndCancel(new PolicyPythonClientException("PYTHON_RESPONSE_TOO_LARGE", false));
                    return bodySubscriber;
                }
                return bodySubscriber;
            });
        } catch (Exception e) {
            bodySubscriber.cancelSubscription();
            throw classifyAsyncException(e);
        }

        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            bodySubscriber.failAndCancel(new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true));
            responseFuture.cancel(true);
            throw new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true);
        }

        HttpResponse<byte[]> response;
        try {
            response = responseFuture.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            bodySubscriber.failAndCancel(new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true));
            responseFuture.cancel(true);
            throw new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true);
        } catch (InterruptedException e) {
            bodySubscriber.failAndCancel(new PolicyPythonClientException("PYTHON_INTERRUPTED", true));
            responseFuture.cancel(true);
            Thread.currentThread().interrupt();
            throw new PolicyPythonClientException("PYTHON_INTERRUPTED", true);
        } catch (ExecutionException e) {
            bodySubscriber.cancelSubscription();
            responseFuture.cancel(true);
            throw classifyAsyncException(e.getCause());
        } catch (Exception e) {
            bodySubscriber.cancelSubscription();
            responseFuture.cancel(true);
            throw classifyAsyncException(e);
        }

        byte[] bodyBytes = response.body();
        if (bodyBytes == null || bodyBytes.length == 0) {
            throw new PolicyPythonClientException("PYTHON_EMPTY_RESPONSE", false);
        }
        try {
            return objectMapper.readValue(bodyBytes, PolicyPythonContracts.Response.class);
        } catch (Exception e) {
            throw new PolicyPythonClientException("PYTHON_INVALID_JSON", false);
        }
    }

    private PolicyPythonClientException statusException(int status) {
        if (status >= 300 && status < 400) {
            return new PolicyPythonClientException("PYTHON_HTTP_REDIRECT_REJECTED", false);
        }
        if (status == 401) {
            return new PolicyPythonClientException("PYTHON_HTTP_401", false);
        }
        if (status == 403) {
            return new PolicyPythonClientException("PYTHON_HTTP_403", false);
        }
        if (status == 429) {
            return new PolicyPythonClientException("PYTHON_HTTP_429", true);
        }
        if (status >= 500 && status < 600) {
            return new PolicyPythonClientException("PYTHON_HTTP_5XX", true);
        }
        return new PolicyPythonClientException("PYTHON_HTTP_" + status, false);
    }

    private PolicyPythonClientException classifyAsyncException(Throwable throwable) {
        Throwable curr = throwable;
        while (curr instanceof ExecutionException || curr instanceof CompletionException) {
            if (curr.getCause() == null) {
                break;
            }
            curr = curr.getCause();
        }
        if (curr instanceof PolicyPythonClientException pce) {
            return pce;
        }
        if (curr instanceof HttpConnectTimeoutException) {
            return new PolicyPythonClientException("PYTHON_CONNECT_TIMEOUT", true);
        }
        if (curr instanceof HttpTimeoutException || curr instanceof TimeoutException) {
            return new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true);
        }
        if (curr instanceof ConnectException) {
            return new PolicyPythonClientException("PYTHON_CONNECT_FAILED", true);
        }
        if (curr instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new PolicyPythonClientException("PYTHON_INTERRUPTED", true);
        }
        if (curr instanceof IOException) {
            // Check nested cause in case JDK HttpClient wrapped a PolicyPythonClientException inside IOException
            Throwable cause = curr.getCause();
            while (cause != null) {
                if (cause instanceof PolicyPythonClientException pce) {
                    return pce;
                }
                if (cause instanceof HttpConnectTimeoutException) {
                    return new PolicyPythonClientException("PYTHON_CONNECT_TIMEOUT", true);
                }
                if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
                    return new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true);
                }
                if (cause instanceof ConnectException) {
                    return new PolicyPythonClientException("PYTHON_CONNECT_FAILED", true);
                }
                cause = cause.getCause();
            }
            return new PolicyPythonClientException("PYTHON_IO_ERROR", true);
        }
        return new PolicyPythonClientException("PYTHON_CLIENT_ERROR", false);
    }

    static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final long deadlineNanos;
        private final CompletableFuture<byte[]> bodyFuture = new CompletableFuture<>();
        private final ByteArrayOutputStream buffer;
        private final AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();
        private final AtomicBoolean done = new AtomicBoolean(false);
        private int totalRead = 0;

        BoundedBodySubscriber(int maxBytes, long deadlineNanos) {
            this.maxBytes = maxBytes;
            this.deadlineNanos = deadlineNanos;
            this.buffer = new ByteArrayOutputStream(Math.min(4096, maxBytes));
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return bodyFuture;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription");
            if (!subscriptionRef.compareAndSet(null, subscription)) {
                subscription.cancel();
                return;
            }
            if (done.get()) {
                subscription.cancel();
                return;
            }
            if (System.nanoTime() > deadlineNanos) {
                failAndCancel(new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true));
                return;
            }
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            Objects.requireNonNull(items, "items");
            if (done.get()) {
                return;
            }
            if (System.nanoTime() > deadlineNanos) {
                failAndCancel(new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true));
                return;
            }
            synchronized (buffer) {
                for (ByteBuffer buf : items) {
                    if (buf == null || !buf.hasRemaining()) {
                        continue;
                    }
                    int len = buf.remaining();
                    if (totalRead + len > maxBytes) {
                        failAndCancel(new PolicyPythonClientException("PYTHON_RESPONSE_TOO_LARGE", false));
                        return;
                    }
                    byte[] chunk = new byte[len];
                    buf.get(chunk);
                    buffer.write(chunk, 0, len);
                    totalRead += len;
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            Objects.requireNonNull(throwable, "throwable");
            if (done.compareAndSet(false, true)) {
                bodyFuture.completeExceptionally(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (done.compareAndSet(false, true)) {
                if (System.nanoTime() > deadlineNanos) {
                    bodyFuture.completeExceptionally(new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true));
                    return;
                }
                synchronized (buffer) {
                    bodyFuture.complete(buffer.toByteArray());
                }
            }
        }

        void failAndCancel(Throwable cause) {
            done.set(true);
            cancelSubscription();
            bodyFuture.completeExceptionally(cause);
        }

        void cancelSubscription() {
            Flow.Subscription sub = subscriptionRef.get();
            if (sub != null) {
                try {
                    sub.cancel();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private byte[] serializeRequest(PolicyPythonContracts.Request request) {
        if (request == null || request.question() == null || request.question().isBlank()
                || request.question().length() > 500) {
            throw new PolicyPythonClientException("PYTHON_INVALID_REQUEST", false);
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(request);
            if (bytes.length > MAX_REQUEST_BYTES) {
                throw new PolicyPythonClientException("PYTHON_REQUEST_TOO_LARGE", false);
            }
            return bytes;
        } catch (PolicyPythonClientException e) {
            throw e;
        } catch (Exception e) {
            throw new PolicyPythonClientException("PYTHON_REQUEST_SERIALIZATION_ERROR", false);
        }
    }

    private String requireValidServiceToken() {
        String rawToken = properties.getAuthToken();
        if (rawToken == null || rawToken.isBlank()) {
            throw new PolicyPythonClientException("PYTHON_AUTH_TOKEN_MISSING", false);
        }
        String trimmed = rawToken.trim();
        if (trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0 || trimmed.contains(" ")) {
            throw new PolicyPythonClientException("PYTHON_AUTH_TOKEN_INVALID", false);
        }
        return trimmed;
    }

    private URI resolveAndValidateTargetUri() {
        String rawBaseUrl = properties.getBaseUrl();
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new PolicyPythonClientException("PYTHON_URL_NOT_CONFIGURED", false);
        }
        URI base;
        try {
            base = URI.create(rawBaseUrl.trim());
        } catch (Exception e) {
            throw new PolicyPythonClientException("PYTHON_INVALID_URL", false);
        }
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        String host = base.getHost();
        if (host == null || host.isBlank()
                || base.getUserInfo() != null
                || base.getRawQuery() != null
                || base.getRawFragment() != null) {
            throw new PolicyPythonClientException("PYTHON_INVALID_URL", false);
        }
        String path = base.getRawPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            throw new PolicyPythonClientException("PYTHON_INVALID_BASE_PATH", false);
        }
        if ("https".equals(scheme)) {
            // TLS transport is allowed for controlled endpoints
        } else if ("http".equals(scheme)) {
            if (isProductionProfile() || !properties.isAllowInsecureLocalHttp() || !isLoopbackHost(host)) {
                throw new PolicyPythonClientException("PYTHON_INSECURE_TRANSPORT_REJECTED", false);
            }
        } else {
            throw new PolicyPythonClientException("PYTHON_INVALID_URL_SCHEME", false);
        }
        try {
            return new URI(scheme, null, host, base.getPort(),
                    PolicyPythonContracts.INTERNAL_POLICY_QUERY_PATH, null, null);
        } catch (Exception e) {
            throw new PolicyPythonClientException("PYTHON_INVALID_URL", false);
        }
    }

    private boolean isProductionProfile() {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .filter(p -> p != null && !p.isBlank())
                .map(p -> p.trim().toLowerCase(Locale.ROOT))
                .anyMatch(p -> "prod".equals(p) || "production".equals(p));
    }

    private boolean isLoopbackHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }

    private HttpClient clientFor(Duration connectTimeout) {
        HttpClient current = this.httpClient;
        if (current != null && connectTimeout.equals(this.cachedConnectTimeout)) {
            return current;
        }
        synchronized (this) {
            if (this.httpClient == null || !connectTimeout.equals(this.cachedConnectTimeout)) {
                this.cachedConnectTimeout = connectTimeout;
                this.httpClient = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(connectTimeout)
                        .build();
            }
            return this.httpClient;
        }
    }
}
