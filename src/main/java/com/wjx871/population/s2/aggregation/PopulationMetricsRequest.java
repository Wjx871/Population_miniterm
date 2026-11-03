package com.wjx871.population.s2.aggregation;

import java.io.Serial;
import java.io.Serializable;

/** Versioned, aggregate-only request shared by the S2 RMI and SOAP demos. */
public class PopulationMetricsRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String schemaVersion = "1.0";
    private String requestId;
    private String traceId;
    private long residentCount;
    private long floatingRegistrationCount;
    private long activeKeyPopulationCount;
    private long activeHouseholdCount;

    public PopulationMetricsRequest() {
    }

    public PopulationMetricsRequest(String requestId, String traceId, long residentCount,
                                    long floatingRegistrationCount, long activeKeyPopulationCount, long activeHouseholdCount) {
        this.requestId = requestId;
        this.traceId = traceId;
        this.residentCount = residentCount;
        this.floatingRegistrationCount = floatingRegistrationCount;
        this.activeKeyPopulationCount = activeKeyPopulationCount;
        this.activeHouseholdCount = activeHouseholdCount;
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
}
