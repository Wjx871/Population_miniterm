package com.wjx871.population.s2.aggregation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PopulationMetricsCalculatorTest {

    private final PopulationMetricsCalculator calculator = new PopulationMetricsCalculator();

    @Test
    void calculatesNamedCountRatiosWithoutCallingThemPercentages() throws Exception {
        PopulationMetricsResult result = calculator.calculate(
                new PopulationMetricsRequest("req-1", "trace-1", 100, 125, 200, 40));

        assertThat(result.getFloatingRegistrationToResidentRatio()).isEqualByComparingTo("1.2500");
        assertThat(result.getActiveKeyPopulationToResidentRatio()).isEqualByComparingTo("2.0000");
        assertThat(result.getResidentToActiveHouseholdRatio()).isEqualByComparingTo("2.5000");
        assertThat(result.getFloatingRegistrationCount()).isEqualTo(125);
    }

    @Test
    void zeroDenominatorsProduceNullRatiosAndKeepValidCounts() throws Exception {
        PopulationMetricsResult result = calculator.calculate(
                new PopulationMetricsRequest("req-2", "trace-2", 0, 5, 3, 0));

        assertThat(result.getFloatingRegistrationToResidentRatio()).isNull();
        assertThat(result.getActiveKeyPopulationToResidentRatio()).isNull();
        assertThat(result.getResidentToActiveHouseholdRatio()).isNull();
        assertThat(result.getFloatingRegistrationCount()).isEqualTo(5);
    }

    @Test
    void acceptsMaximumSignedLongCountWithoutMultiplicationOverflow() throws Exception {
        PopulationMetricsResult result = calculator.calculate(
                new PopulationMetricsRequest("req-3", "trace-3", Long.MAX_VALUE, Long.MAX_VALUE, 1, 1));

        assertThat(result.getResidentCount()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.getFloatingRegistrationToResidentRatio()).isEqualByComparingTo("1.0000");
        assertThat(result.getActiveKeyPopulationToResidentRatio()).isEqualByComparingTo("0.0000");
        assertThat(result.getResidentToActiveHouseholdRatio())
                .isEqualByComparingTo("9223372036854775807.0000");
    }

    @Test
    void rejectsNegativeCountsAndUnsupportedProtocolValues() {
        assertThatThrownBy(() -> calculator.calculate(
                new PopulationMetricsRequest("req-4", "trace-4", Long.MIN_VALUE, 0, 0, 0)))
                .isInstanceOf(InvalidPopulationMetricsException.class)
                .extracting(exception -> ((InvalidPopulationMetricsException) exception).getFaultInfo().getCode())
                .isEqualTo("NEGATIVE_COUNT");

        PopulationMetricsRequest unsupported = new PopulationMetricsRequest("req-5", "trace-5", 0, 0, 0, 0);
        unsupported.setSchemaVersion("2.0");
        assertThatThrownBy(() -> calculator.calculate(unsupported))
                .isInstanceOf(InvalidPopulationMetricsException.class)
                .extracting(exception -> ((InvalidPopulationMetricsException) exception).getFaultInfo().getCode())
                .isEqualTo("UNSUPPORTED_SCHEMA");
    }

    @Test
    void rejectsUnsafeCorrelationIds() {
        assertThatThrownBy(() -> calculator.calculate(
                new PopulationMetricsRequest("request\r\nforged", "trace-6", 0, 0, 0, 0)))
                .isInstanceOf(InvalidPopulationMetricsException.class)
                .extracting(exception -> ((InvalidPopulationMetricsException) exception).getFaultInfo().getCode())
                .isEqualTo("INVALID_CORRELATION_ID");
    }
}
