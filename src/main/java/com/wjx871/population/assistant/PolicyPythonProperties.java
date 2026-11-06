package com.wjx871.population.assistant;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "policy.python")
public class PolicyPythonProperties {
    private boolean enabled = false;
    private ClientMode client = ClientMode.MOCK;
    private String baseUrl = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration requestTimeout = Duration.ofSeconds(8);
    private String authToken = "";
    private boolean allowInsecureLocalHttp = false;
    private MockMode mockMode = MockMode.SUCCESS;
    private Duration mockDelay = Duration.ZERO;

    public Duration effectiveConnectTimeout() {
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            return Duration.ofSeconds(2);
        }
        return connectTimeout.compareTo(Duration.ofSeconds(10)) > 0 ? Duration.ofSeconds(10) : connectTimeout;
    }

    public Duration effectiveRequestTimeout() {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            return Duration.ofSeconds(8);
        }
        return requestTimeout.compareTo(Duration.ofSeconds(15)) > 0 ? Duration.ofSeconds(15) : requestTimeout;
    }

    public enum ClientMode {
        MOCK,
        HTTP
    }

    public enum MockMode {
        SUCCESS,
        EXTERNAL_UNREGISTERED_SOURCE,
        TAMPERED_REGISTERED_SOURCE,
        INSUFFICIENT_EVIDENCE,
        TIMEOUT,
        CONNECT_FAILURE,
        HTTP_401,
        HTTP_403,
        HTTP_429,
        HTTP_500,
        HTTP_503,
        INVALID_JSON,
        UNSUPPORTED_SCHEMA,
        MISMATCHED_REQUEST_ID,
        INCOMPLETE_RESPONSE,
        EMPTY_CITATIONS,
        UNKNOWN_CITATION_ID_USED,
        UNDECLARED_ANSWER_CITATION,
        DUPLICATE_SOURCE_ID,
        INTENT_MISMATCH_CATEGORY,
        REGION_MISMATCH,
        EXPIRED_CITATION,
        INVALID_SOURCE_VERSION,
        UNSAFE_ANSWER_INSTRUCTION
    }
}
