package com.wjx871.population.s2.aggregation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Pure calculation component. It has no Spring, database, or security-context dependency. */
public final class PopulationMetricsCalculator {

    private static final String SCHEMA_VERSION = "1.0";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final int RATIO_SCALE = 4;

    public PopulationMetricsResult calculate(PopulationMetricsRequest request)
            throws InvalidPopulationMetricsException {
        validate(request);

        PopulationMetricsResult result = new PopulationMetricsResult();
        result.setSchemaVersion(SCHEMA_VERSION);
        result.setRequestId(request.getRequestId());
        result.setTraceId(request.getTraceId());
        result.setResidentCount(request.getResidentCount());
        result.setFloatingRegistrationCount(request.getFloatingRegistrationCount());
        result.setActiveKeyPopulationCount(request.getActiveKeyPopulationCount());
        result.setActiveHouseholdCount(request.getActiveHouseholdCount());
        result.setFloatingRegistrationToResidentRatio(ratio(request.getFloatingRegistrationCount(), request.getResidentCount()));
        result.setActiveKeyPopulationToResidentRatio(ratio(request.getActiveKeyPopulationCount(), request.getResidentCount()));
        result.setResidentToActiveHouseholdRatio(ratio(request.getResidentCount(), request.getActiveHouseholdCount()));
        return result;
    }

    private void validate(PopulationMetricsRequest request) throws InvalidPopulationMetricsException {
        if (request == null) {
            throw invalid("INVALID_REQUEST", "request is required");
        }
        if (!SCHEMA_VERSION.equals(request.getSchemaVersion())) {
            throw invalid("UNSUPPORTED_SCHEMA", "schemaVersion must be 1.0");
        }
        if (!isSafeId(request.getRequestId()) || !isSafeId(request.getTraceId())) {
            throw invalid("INVALID_CORRELATION_ID", "requestId and traceId must contain 1-64 safe ASCII characters");
        }
        if (request.getResidentCount() < 0 || request.getFloatingRegistrationCount() < 0
                || request.getActiveKeyPopulationCount() < 0 || request.getActiveHouseholdCount() < 0) {
            throw invalid("NEGATIVE_COUNT", "counts must be in the range 0..Long.MAX_VALUE");
        }
    }

    private boolean isSafeId(String value) {
        return value != null && SAFE_ID.matcher(value).matches();
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private InvalidPopulationMetricsException invalid(String code, String message) {
        return new InvalidPopulationMetricsException(code, message);
    }
}
