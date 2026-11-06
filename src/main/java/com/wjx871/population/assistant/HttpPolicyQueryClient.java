package com.wjx871.population.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.s2.trace.S2TraceContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.OptionalLong;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class HttpPolicyQueryClient implements PolicyQueryClient {
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;
    public static final int MAX_REQUEST_BYTES = 32 * 1024;
    private static final int STREAM_BUFFER_SIZE = 4096;

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

        HttpResponse<InputStream> response;
        try {
            response = clientFor(properties.effectiveConnectTimeout())
                    .send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpConnectTimeoutException e) {
            throw new PolicyPythonClientException("PYTHON_CONNECT_TIMEOUT", true);
        } catch (HttpTimeoutException e) {
            throw new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true);
        } catch (ConnectException e) {
            throw new PolicyPythonClientException("PYTHON_CONNECT_FAILED", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PolicyPythonClientException("PYTHON_INTERRUPTED", true);
        } catch (IOException e) {
            throw new PolicyPythonClientException("PYTHON_IO_ERROR", true);
        } catch (Exception e) {
            throw new PolicyPythonClientException("PYTHON_CLIENT_ERROR", false);
        }

        try (InputStream bodyStream = response.body()) {
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                throw new PolicyPythonClientException("PYTHON_HTTP_REDIRECT_REJECTED", false);
            }
            if (status == 401) {
                throw new PolicyPythonClientException("PYTHON_HTTP_401", false);
            }
            if (status == 403) {
                throw new PolicyPythonClientException("PYTHON_HTTP_403", false);
            }
            if (status == 429) {
                throw new PolicyPythonClientException("PYTHON_HTTP_429", true);
            }
            if (status >= 500 && status < 600) {
                throw new PolicyPythonClientException("PYTHON_HTTP_5XX", true);
            }
            if (status != 200) {
                throw new PolicyPythonClientException("PYTHON_HTTP_" + status, false);
            }

            OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
            if (contentLength.isPresent() && contentLength.getAsLong() > MAX_RESPONSE_BYTES) {
                throw new PolicyPythonClientException("PYTHON_RESPONSE_TOO_LARGE", false);
            }

            byte[] bodyBytes = readBoundedStream(bodyStream, deadlineNanos);
            try {
                return objectMapper.readValue(bodyBytes, PolicyPythonContracts.Response.class);
            } catch (Exception e) {
                throw new PolicyPythonClientException("PYTHON_INVALID_JSON", false);
            }
        } catch (PolicyPythonClientException e) {
            throw e;
        } catch (IOException e) {
            throw new PolicyPythonClientException("PYTHON_STREAM_IO_ERROR", true);
        }
    }

    private byte[] readBoundedStream(InputStream bodyStream, long deadlineNanos) throws IOException {
        if (bodyStream == null) {
            throw new PolicyPythonClientException("PYTHON_EMPTY_RESPONSE", false);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(4096, MAX_RESPONSE_BYTES));
        byte[] chunk = new byte[STREAM_BUFFER_SIZE];
        int totalRead = 0;
        while (true) {
            if (System.nanoTime() > deadlineNanos) {
                throw new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true);
            }
            int read;
            try {
                read = bodyStream.read(chunk);
            } catch (HttpTimeoutException e) {
                throw new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true);
            }
            if (read == -1) {
                break;
            }
            totalRead += read;
            if (totalRead > MAX_RESPONSE_BYTES) {
                throw new PolicyPythonClientException("PYTHON_RESPONSE_TOO_LARGE", false);
            }
            buffer.write(chunk, 0, read);
        }
        if (totalRead == 0) {
            throw new PolicyPythonClientException("PYTHON_EMPTY_RESPONSE", false);
        }
        return buffer.toByteArray();
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
