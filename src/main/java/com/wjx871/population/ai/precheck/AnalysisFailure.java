package com.wjx871.population.ai.precheck;

public class AnalysisFailure extends RuntimeException {
    private final String safeCode;
    private final boolean retryable;

    public AnalysisFailure(String safeCode, boolean retryable) {
        super(safeCode);
        this.safeCode = safeCode;
        this.retryable = retryable;
    }

    public String safeCode() {
        return safeCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
