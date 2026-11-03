package com.wjx871.population.s2.aggregation;

public class PopulationRemoteCallException extends RuntimeException {
    private final String errorCode;

    public PopulationRemoteCallException(String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
