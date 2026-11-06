package com.wjx871.population.cache;

/** Internal failure classification; never send the cause or its message to clients. */
public class StrictRedisException extends RuntimeException {
    private final String category;

    public StrictRedisException(String category, Throwable cause) {
        super("Security Redis operation unavailable", cause);
        this.category = category;
    }

    public String category() { return category; }
}
