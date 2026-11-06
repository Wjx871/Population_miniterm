package com.wjx871.population.assistant;

import lombok.Getter;

@Getter
public class PolicyPythonClientException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    public PolicyPythonClientException(String errorCode, boolean retryable) {
        super(errorCode);
        this.errorCode = errorCode == null || errorCode.isBlank() ? "PYTHON_CLIENT_ERROR" : errorCode;
        this.retryable = retryable;
    }
}
