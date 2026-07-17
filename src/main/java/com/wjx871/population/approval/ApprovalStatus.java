package com.wjx871.population.approval;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    /** Legacy terminal status retained for reading historical approval records. */
    COMPLETED
}
