package com.wjx871.population.ai.precheck;

public class AiMessagePoisonException extends RuntimeException {
    private final String safeCode;
    private final AiPrecheckTaskService.EventEnvelope event;

    public AiMessagePoisonException(String safeCode) {
        this(safeCode, null);
    }

    public AiMessagePoisonException(String safeCode, AiPrecheckTaskService.EventEnvelope event) {
        super(safeCode);
        this.safeCode = safeCode;
        this.event = event;
    }

    public String safeCode() {
        return safeCode;
    }

    public AiPrecheckTaskService.EventEnvelope event() {
        return event;
    }
}
