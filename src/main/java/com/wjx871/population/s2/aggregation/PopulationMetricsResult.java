package com.wjx871.population.s2.aggregation;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/** Aggregate counts and count ratios; ratio values are not percentages or population shares. */
public class PopulationMetricsResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String schemaVersion = "1.0";
    private String requestId;
    private String traceId;
    private long residentCount;
    private long floatingRegistrationCount;
    private long activeKeyPopulationCount;
    private long activeHouseholdCount;
    private BigDecimal floatingRegistrationToResidentRatio;
    private BigDecimal activeKeyPopulationToResidentRatio;
    private BigDecimal residentToActiveHouseholdRatio;

    public PopulationMetricsResult() {
    }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public long getResidentCount() { return residentCount; }
    public void setResidentCount(long residentCount) { this.residentCount = residentCount; }
    public long getFloatingRegistrationCount() { return floatingRegistrationCount; }
    public void setFloatingRegistrationCount(long floatingRegistrationCount) { this.floatingRegistrationCount = floatingRegistrationCount; }
    public long getActiveKeyPopulationCount() { return activeKeyPopulationCount; }
    public void setActiveKeyPopulationCount(long activeKeyPopulationCount) { this.activeKeyPopulationCount = activeKeyPopulationCount; }
    public long getActiveHouseholdCount() { return activeHouseholdCount; }
    public void setActiveHouseholdCount(long activeHouseholdCount) { this.activeHouseholdCount = activeHouseholdCount; }
    public BigDecimal getFloatingRegistrationToResidentRatio() { return floatingRegistrationToResidentRatio; }
    public void setFloatingRegistrationToResidentRatio(BigDecimal floatingRegistrationToResidentRatio) { this.floatingRegistrationToResidentRatio = floatingRegistrationToResidentRatio; }
    public BigDecimal getActiveKeyPopulationToResidentRatio() { return activeKeyPopulationToResidentRatio; }
    public void setActiveKeyPopulationToResidentRatio(BigDecimal activeKeyPopulationToResidentRatio) { this.activeKeyPopulationToResidentRatio = activeKeyPopulationToResidentRatio; }
    public BigDecimal getResidentToActiveHouseholdRatio() { return residentToActiveHouseholdRatio; }
    public void setResidentToActiveHouseholdRatio(BigDecimal residentToActiveHouseholdRatio) { this.residentToActiveHouseholdRatio = residentToActiveHouseholdRatio; }
}
